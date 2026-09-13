package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import com.rastroos.config.ExtractionProperties;
import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.BusinessRuleException;
import com.rastroos.domain.exception.InvalidUploadException;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.InvoiceImportResult;
import com.rastroos.web.dto.InvoiceReviewItem;
import com.rastroos.web.dto.InvoiceReviewView;
import com.rastroos.web.form.InvoiceImportForm;
import com.rastroos.web.form.InvoiceItemForm;

@ExtendWith(MockitoExtension.class)
class InvoiceImportServiceTest {

    private static final LocalDate OCT = LocalDate.of(2026, 10, 10);

    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @Mock private CategoryRepository categories;
    @Mock private InvoiceVisionReader reader;
    @Mock private ApplicationEventPublisher events;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();
    private final List<Transaction> db = new ArrayList<>();

    private InvoiceImportService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceImportService(accounts, transactions, categories, reader,
                new ExtractionProperties(), events);
        lenient().when(categories.findAllByOrderBySortOrderAsc()).thenReturn(List.of(category("outros")));
        lenient().when(transactions.findAllByUserIdAndAccountIdAndDueDateGreaterThanEqual(
                eq(alice), eq(cardId), any())).thenAnswer(inv -> List.copyOf(db));
    }

    // ── Leitura → conferência ───────────────────────────────────────────

    @Test
    void read_montaConferenciaComStatusEParcelasFuturas_semGravarNada() {
        when(accounts.findByIdAndUserId(cardId, alice)).thenReturn(Optional.of(card(AccountKind.CARD)));
        when(reader.read(eq(alice), any(), anyMap())).thenReturn(new InvoiceReading(OCT,
                new BigDecimal("300.00"), "1234", List.of(
                        line("LOJA X", "100.00", 3, 10, InvoiceLineType.PURCHASE),
                        line("UBER", "15.90", null, null, InvoiceLineType.PURCHASE),
                        line("PAGAMENTO RECEBIDO", "900.00", null, null, InvoiceLineType.PAYMENT))));
        existing("UBER", 1590, OCT, null, null);

        InvoiceReviewView view = service.read(alice, cardId, pdf(), YearMonth.of(2026, 9));

        assertThat(view.hasError()).isFalse();
        assertThat(view.dueDate()).isEqualTo(OCT);
        assertThat(view.items()).extracting(InvoiceReviewItem::status).containsExactly("new", "existing");
        InvoiceReviewItem parcel = view.items().get(0);
        assertThat(parcel.selected()).isTrue();
        assertThat(parcel.futureToCreate()).isEqualTo(7);
        assertThat(view.items().get(1).selectable()).isFalse();
        assertThat(view.ignored()).extracting(InvoiceReviewItem::description).containsExactly("PAGAMENTO RECEBIDO");
        assertThat(view.ignored().get(0).index()).isEqualTo(2);
        verify(transactions, never()).saveAll(anyList());
    }

    @Test
    void read_semVencimentoLegivel_usaODiaDoCartaoNoMesAberto() {
        Account card = card(AccountKind.CARD);
        card.setDueDay((short) 15);
        when(accounts.findByIdAndUserId(cardId, alice)).thenReturn(Optional.of(card));
        when(reader.read(eq(alice), any(), anyMap())).thenReturn(new InvoiceReading(null, null, null,
                List.of(line("LOJA", "10.00", null, null, InvoiceLineType.PURCHASE))));

        InvoiceReviewView view = service.read(alice, cardId, pdf(), YearMonth.of(2026, 9));

        assertThat(view.dueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(view.dueDateRead()).isFalse();
    }

    @Test
    void read_arquivoInvalido_naoChamaAIA() {
        when(accounts.findByIdAndUserId(cardId, alice)).thenReturn(Optional.of(card(AccountKind.CARD)));
        MockMultipartFile exe = new MockMultipartFile("file", "fatura.exe", "application/octet-stream",
                new byte[] {0x4D, 0x5A});

        assertThatThrownBy(() -> service.read(alice, cardId, exe, YearMonth.of(2026, 9)))
                .isInstanceOf(InvalidUploadException.class);
        verifyNoInteractions(reader);
    }

    @Test
    void read_contaDeOutroUsuario_404() {
        when(accounts.findByIdAndUserId(cardId, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(bob, cardId, pdf(), YearMonth.of(2026, 9)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(reader);
    }

    @Test
    void read_contaQueNaoECartaoDeCredito_recusa() {
        when(accounts.findByIdAndUserId(cardId, alice)).thenReturn(Optional.of(card(AccountKind.DEBIT)));

        assertThatThrownBy(() -> service.read(alice, cardId, pdf(), YearMonth.of(2026, 9)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("account.invoice.notCredit");
        verifyNoInteractions(reader);
    }

    // ── Gravação ─────────────────────────────────────────────────────────

    @Test
    void commit_criaParcelaDoMesESeguintesNaMesmaSerie_emAberto() {
        lockCard();
        InvoiceImportForm form = form(item("LOJA X", "100.00", 3, 10, true));

        InvoiceImportResult result = service.commit(alice, cardId, form);

        List<Transaction> saved = savedRows();
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.futureCreated()).isEqualTo(7);
        assertThat(result.month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(saved).hasSize(8);
        assertThat(saved).extracting(Transaction::getInstallmentCurrent)
                .containsExactly((short) 3, (short) 4, (short) 5, (short) 6, (short) 7, (short) 8, (short) 9, (short) 10);
        assertThat(saved).extracting(Transaction::getDueDate)
                .startsWith(OCT, LocalDate.of(2026, 11, 10))
                .endsWith(LocalDate.of(2027, 5, 10));
        assertThat(saved).extracting(Transaction::getSeriesId).doesNotContainNull()
                .containsOnly(saved.get(0).getSeriesId());
        assertThat(saved).extracting(Transaction::getUserId).containsOnly(alice);
        assertThat(saved).extracting(Transaction::getAccountId).containsOnly(cardId);
        assertThat(saved).extracting(Transaction::isPaid).containsOnly(false);
        assertThat(saved).extracting(Transaction::getAmountCents).containsOnly(10000L);
        verify(events).publishEvent(any(UserDataChangedEvent.class));
    }

    @Test
    void commit_compraAVistaNaoTemSerie() {
        lockCard();

        service.commit(alice, cardId, form(item("UBER", "15.90", null, null, true)));

        assertThat(savedRows()).singleElement().satisfies(t -> {
            assertThat(t.getSeriesId()).isNull();
            assertThat(t.getInstallmentTotal()).isNull();
            assertThat(t.getAmountCents()).isEqualTo(1590);
        });
    }

    /** Duplo clique, duas abas ou formulário adulterado: o que já existe não é criado de novo. */
    @Test
    void commit_itemQueJaExiste_naoDuplicaMesmoMarcado() {
        lockCard();
        existing("UBER", 1590, OCT, null, null);

        InvoiceImportResult result = service.commit(alice, cardId, form(item("UBER", "15.90", null, null, true)));

        assertThat(result.nothingChanged()).isTrue();
        verify(transactions, never()).saveAll(anyList());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void commit_itemDesmarcado_naoEntra() {
        lockCard();

        InvoiceImportResult result = service.commit(alice, cardId, form(item("UBER", "15.90", null, null, false)));

        assertThat(result.created()).isZero();
        verify(transactions, never()).saveAll(anyList());
    }

    @Test
    void commit_possivelDuplicadoMarcado_entraPorqueAPessoaConfirmou() {
        lockCard();
        existing("Almoço", 4590, OCT, null, null);

        InvoiceImportResult result = service.commit(alice, cardId,
                form(item("RESTAURANTE SABOR", "45.90", null, null, true)));

        assertThat(result.created()).isEqualTo(1);
    }

    @Test
    void commit_ajusteDeCentavosMarcado_corrigeOValorDaParcelaExistente() {
        lockCard();
        Transaction projected = existing("LOJA X", 3333, OCT, (short) 10, (short) 10);

        InvoiceImportResult result = service.commit(alice, cardId, form(item("LOJA X", "33.37", 10, 10, true)));

        assertThat(result.adjusted()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(projected.getAmountCents()).isEqualTo(3337);
        assertThat(savedRows()).containsExactly(projected);
    }

    @Test
    void commit_creditoEPagamento_nuncaViramLancamento() {
        lockCard();
        InvoiceItemForm credit = item("ESTORNO LOJA", "50.00", null, null, true);
        credit.setType(InvoiceLineType.CREDIT);

        InvoiceImportResult result = service.commit(alice, cardId, form(credit));

        assertThat(result.nothingChanged()).isTrue();
        verify(transactions, never()).saveAll(anyList());
    }

    @Test
    void commit_categoriaInexistente_404() {
        lockCard();
        InvoiceItemForm forged = item("UBER", "15.90", null, null, true);
        forged.setCategoryId("nao-existe");

        assertThatThrownBy(() -> service.commit(alice, cardId, form(forged)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactions, never()).saveAll(anyList());
    }

    @Test
    void commit_travaAContaDoProprioUsuario_eContaAlheiaDa404() {
        when(accounts.lockByIdAndUserId(cardId, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commit(bob, cardId, form(item("UBER", "15.90", null, null, true))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactions, never()).saveAll(anyList());
    }

    @Test
    void commit_contaQueNaoECartaoDeCredito_recusa() {
        when(accounts.lockByIdAndUserId(cardId, alice)).thenReturn(Optional.of(card(AccountKind.BILL)));

        assertThatThrownBy(() -> service.commit(alice, cardId, form(item("UBER", "15.90", null, null, true))))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── Apoio ────────────────────────────────────────────────────────────

    private void lockCard() {
        when(accounts.lockByIdAndUserId(cardId, alice)).thenReturn(Optional.of(card(AccountKind.CARD)));
    }

    @SuppressWarnings("unchecked")
    private List<Transaction> savedRows() {
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        return captor.getValue();
    }

    private Account card(AccountKind kind) {
        Account a = new Account();
        a.setId(cardId);
        a.setUserId(alice);
        a.setName("Nubank");
        a.setKind(kind);
        return a;
    }

    private Transaction existing(String description, long cents, LocalDate due, Short current, Short total) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setUserId(alice);
        t.setAccountId(cardId);
        t.setDescription(description);
        t.setAmountCents(cents);
        t.setDueDate(due);
        t.setInstallmentCurrent(current);
        t.setInstallmentTotal(total);
        t.setCreatedAt(Instant.now());
        db.add(t);
        return t;
    }

    private static InvoiceLine line(String description, String amount, Integer current, Integer total,
                                    InvoiceLineType type) {
        return new InvoiceLine(null, description, new BigDecimal(amount),
                current == null ? null : current.shortValue(),
                total == null ? null : total.shortValue(), type, "outros");
    }

    private static InvoiceItemForm item(String description, String amount, Integer current, Integer total,
                                        boolean selected) {
        InvoiceItemForm item = new InvoiceItemForm();
        item.setDescription(description);
        item.setAmount(new BigDecimal(amount));
        item.setInstallment(current == null ? null : current.shortValue());
        item.setInstallments(total == null ? null : total.shortValue());
        item.setType(InvoiceLineType.PURCHASE);
        item.setCategoryId("outros");
        item.setSelected(selected);
        return item;
    }

    private static InvoiceImportForm form(InvoiceItemForm... items) {
        InvoiceImportForm form = new InvoiceImportForm();
        form.setDueDate(OCT);
        form.getItems().addAll(List.of(items));
        return form;
    }

    private static Category category(String id) {
        Category c = new Category();
        c.setId(id);
        c.setNamePt("Outros");
        c.setNameEn("Other");
        c.setColorHex("#94a3b8");
        return c;
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "fatura.pdf", "application/pdf",
                "%PDF-1.7\nconteudo".getBytes(StandardCharsets.ISO_8859_1));
    }
}

package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.form.TransactionForm;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository txRepo;
    @Mock private AccountRepository accountsRepo;
    @Mock private CategoryRepository categoriesRepo;

    @InjectMocks private TransactionService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob   = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Test
    void createParcelaEm3xDivideOTotalEComecaNoMesSeguinte() {
        TransactionForm form = makeForm("Geladeira", new BigDecimal("450.00"),
                LocalDate.of(2026, 5, 10), 3);

        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        when(categoriesRepo.existsById("outros")).thenReturn(true);
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> created = service.create(alice, form);

        assertThat(created).hasSize(3);
        // 1ª parcela cai no mês seguinte ao vencimento informado (maio → junho).
        assertThat(created).extracting(Transaction::getDueDate)
                .containsExactly(
                        LocalDate.of(2026, 6, 10),
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 10));
        assertThat(created).extracting(Transaction::getInstallmentCurrent)
                .containsExactly((short) 1, (short) 2, (short) 3);
        assertThat(created).extracting(Transaction::getInstallmentTotal)
                .containsOnly((short) 3);
        // 450,00 dividido em 3 → 150,00 por parcela (não o total em cada uma).
        assertThat(created).extracting(Transaction::getAmountCents)
                .containsOnly(15_000L);
        assertThat(created).extracting(Transaction::isPaid).containsOnly(false);
        verify(txRepo, times(3)).save(any(Transaction.class));
    }

    @Test
    void createParcelaComRestoDistribuiCentavosNasPrimeirasEFechaOTotal() {
        TransactionForm form = makeForm("Notebook", new BigDecimal("100.00"),
                LocalDate.of(2026, 5, 10), 3);

        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        when(categoriesRepo.existsById("outros")).thenReturn(true);
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> created = service.create(alice, form);

        // 10000 centavos / 3 = 3333 com resto 1 → primeira parcela absorve o centavo.
        assertThat(created).extracting(Transaction::getAmountCents)
                .containsExactly(3_334L, 3_333L, 3_333L);
        long sum = created.stream().mapToLong(Transaction::getAmountCents).sum();
        assertThat(sum).isEqualTo(10_000L); // soma fecha exatamente o total
    }

    @Test
    void createParcelaComValorMenorQueNumeroDeParcelasLancaErroENaoSalva() {
        // 0,03 em 4x → 3 centavos não dividem em 4 parcelas > 0.
        TransactionForm form = makeForm("Bala", new BigDecimal("0.03"),
                LocalDate.of(2026, 5, 10), 4);

        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        when(categoriesRepo.existsById("outros")).thenReturn(true);

        assertThatThrownBy(() -> service.create(alice, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transaction.installmentTooSmall");

        verify(txRepo, never()).save(any(Transaction.class));
    }

    @Test
    void createSemParcelasGeraUmRegistroSemInstallmentMetadata() {
        TransactionForm form = makeForm("Mercado", new BigDecimal("123.45"),
                LocalDate.of(2026, 5, 3), 1);
        form.setPaid(true);

        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        when(categoriesRepo.existsById("outros")).thenReturn(true);
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> created = service.create(alice, form);

        assertThat(created).hasSize(1);
        Transaction t = created.get(0);
        assertThat(t.getInstallmentCurrent()).isNull();
        assertThat(t.getInstallmentTotal()).isNull();
        assertThat(t.getAmountCents()).isEqualTo(12_345L);
        // À vista (1x) mantém o vencimento informado — não desloca para o mês seguinte.
        assertThat(t.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 3));
        assertThat(t.isPaid()).isTrue();
        assertThat(t.getPaidAt()).isNotNull();
    }

    @Test
    void createComFixedMarcaApenasOPrimeiroComoFixed() {
        TransactionForm form = makeForm("Aluguel", new BigDecimal("1500.00"),
                LocalDate.of(2026, 5, 5), 1);
        form.setFixed(true);

        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        when(categoriesRepo.existsById("outros")).thenReturn(true);
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> created = service.create(alice, form);

        assertThat(created.get(0).isFixed()).isTrue();
    }

    @Test
    void createComContaDeOutroUsuarioLancaNotFound() {
        TransactionForm form = makeForm("Algo", new BigDecimal("10.00"),
                LocalDate.now(), 1);

        // Bob é o dono; Alice não enxerga
        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(alice, form))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("account.notFound");

        verify(txRepo, never()).save(any(Transaction.class));
    }

    @Test
    void createComCategoriaInexistenteLancaNotFound() {
        TransactionForm form = makeForm("Algo", new BigDecimal("10.00"),
                LocalDate.now(), 1);

        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        when(categoriesRepo.existsById("outros")).thenReturn(false);

        assertThatThrownBy(() -> service.create(alice, form))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("category.notFound");

        verify(txRepo, never()).save(any(Transaction.class));
    }

    @Test
    void togglePaidDePagoVoltaParaAberto() {
        UUID id = UUID.randomUUID();
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(alice);
        t.setPaid(true);
        t.setPaidAt(java.time.Instant.now());

        when(txRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(t));
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = service.togglePaid(alice, id);

        assertThat(result.isPaid()).isFalse();
        assertThat(result.getPaidAt()).isNull();
    }

    @Test
    void togglePaidDeAbertoMarcaComoPagoComPaidAt() {
        UUID id = UUID.randomUUID();
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(alice);
        t.setPaid(false);

        when(txRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(t));
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = service.togglePaid(alice, id);

        assertThat(result.isPaid()).isTrue();
        assertThat(result.getPaidAt()).isNotNull();
    }

    @Test
    void requireDeContaDeOutroUsuarioLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(txRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(alice, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatePreservaPaidAtSeJaEstavaPagoEAindaEsta() {
        UUID id = UUID.randomUUID();
        java.time.Instant originalPaidAt = java.time.Instant.parse("2026-05-01T10:00:00Z");
        Transaction existing = new Transaction();
        existing.setId(id);
        existing.setUserId(alice);
        existing.setAccountId(accountId);
        existing.setCategoryId("outros");
        existing.setDescription("Old");
        existing.setAmountCents(1_000L);
        existing.setDueDate(LocalDate.of(2026, 4, 5));
        existing.setPaid(true);
        existing.setPaidAt(originalPaidAt);

        TransactionForm form = makeForm("New", new BigDecimal("12.50"),
                LocalDate.of(2026, 5, 7), 1);
        form.setPaid(true);

        when(txRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(existing));
        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        when(categoriesRepo.existsById("outros")).thenReturn(true);
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction updated = service.update(alice, id, form);

        assertThat(updated.getDescription()).isEqualTo("New");
        assertThat(updated.getAmountCents()).isEqualTo(1_250L);
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(updated.isPaid()).isTrue();
        // ainda estava pago — paidAt preservado
        assertThat(updated.getPaidAt()).isEqualTo(originalPaidAt);
    }

    @Test
    void updateReabrePagamentoLimpaPaidAt() {
        UUID id = UUID.randomUUID();
        Transaction existing = new Transaction();
        existing.setId(id);
        existing.setUserId(alice);
        existing.setAccountId(accountId);
        existing.setCategoryId("outros");
        existing.setDescription("Old");
        existing.setAmountCents(1_000L);
        existing.setDueDate(LocalDate.of(2026, 4, 5));
        existing.setPaid(true);
        existing.setPaidAt(java.time.Instant.now());

        TransactionForm form = makeForm("Old", new BigDecimal("10.00"),
                LocalDate.of(2026, 4, 5), 1);
        form.setPaid(false);

        when(txRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(existing));
        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        when(categoriesRepo.existsById("outros")).thenReturn(true);
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction updated = service.update(alice, id, form);

        assertThat(updated.isPaid()).isFalse();
        assertThat(updated.getPaidAt()).isNull();
    }

    @Test
    void deleteRemoveSomenteAposVerificarOwnership() {
        UUID id = UUID.randomUUID();
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(alice);
        when(txRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(t));

        service.delete(alice, id);

        verify(txRepo).delete(t);
    }

    @Test
    void deleteDeOutroUsuarioLancaNotFoundENaoChamaDelete() {
        UUID id = UUID.randomUUID();
        when(txRepo.findByIdAndUserId(id, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(bob, id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(txRepo, never()).delete(any(Transaction.class));
    }

    @Test
    void createSeProcuraPorContaUsandoOwnerCorreto() {
        // garantir que o accountId é olhado COM userId — evita IDOR
        TransactionForm form = makeForm("Algo", new BigDecimal("1.00"),
                LocalDate.now(), 1);

        when(accountsRepo.findByIdAndUserId(accountId, alice))
                .thenReturn(Optional.of(accountOf(alice)));
        lenient().when(categoriesRepo.existsById("outros")).thenReturn(true);
        lenient().when(txRepo.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.create(alice, form);

        ArgumentCaptor<UUID> aIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(accountsRepo).findByIdAndUserId(aIdCaptor.capture(), userIdCaptor.capture());
        assertThat(aIdCaptor.getValue()).isEqualTo(accountId);
        assertThat(userIdCaptor.getValue()).isEqualTo(alice);
    }

    // ── helpers ──────────────────────────────────────────────

    private TransactionForm makeForm(String desc, BigDecimal amount,
                                     LocalDate dueDate, int installments) {
        TransactionForm f = new TransactionForm();
        f.setDescription(desc);
        f.setAccountId(accountId);
        f.setCategoryId("outros");
        f.setAmount(amount);
        f.setDueDate(dueDate);
        f.setInstallments(installments);
        f.setFixed(false);
        f.setPaid(false);
        return f;
    }

    private Account accountOf(UUID userId) {
        Account a = new Account();
        a.setId(accountId);
        a.setUserId(userId);
        a.setName("Cartão");
        a.setKind(AccountKind.CARD);
        return a;
    }
}

package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.domain.repository.UserRepository;
import com.rastroos.web.dto.InvoiceImportResult;
import com.rastroos.web.form.InvoiceImportForm;
import com.rastroos.web.form.InvoiceItemForm;

/**
 * O fluxo inteiro contra o Postgres de verdade: importar a fatura, importar de
 * novo, importar a do mês seguinte e apagar parte das parcelas — com as
 * consultas reais (trava da conta, série, filtro por usuário).
 *
 * <p>Usuários e cartões criados aqui mesmo; a transação do teste desfaz tudo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceImportFlowTest {

    private static final LocalDate OCT = LocalDate.of(2031, 10, 10);
    private static final LocalDate NOV = LocalDate.of(2031, 11, 10);

    @Autowired private InvoiceImportService imports;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactions;
    @Autowired private AccountRepository accounts;
    @Autowired private UserRepository users;

    private UUID alice;
    private UUID bob;
    private UUID aliceCard;
    private UUID bobCard;

    @BeforeEach
    void setUp() {
        alice = user("alice-fatura").getId();
        bob = user("bob-fatura").getId();
        aliceCard = card(alice).getId();
        bobCard = card(bob).getId();
    }

    @Test
    void mesmaFaturaDuasVezes_eDepoisAFaturaSeguinte_nuncaDuplicam() {
        InvoiceImportResult first = imports.commit(alice, aliceCard, october());
        assertThat(first.created()).isEqualTo(2);
        assertThat(first.futureCreated()).isEqualTo(7);
        assertThat(aliceRows()).hasSize(9);

        InvoiceImportResult again = imports.commit(alice, aliceCard, october());
        assertThat(again.nothingChanged()).isTrue();
        assertThat(aliceRows()).hasSize(9);

        InvoiceImportForm november = form(NOV,
                item("LOJA X", "100.00", 4, 10),
                item("IFOOD", "45.90", null, null));
        InvoiceImportResult next = imports.commit(alice, aliceCard, november);
        assertThat(next.created()).isEqualTo(1);
        assertThat(next.futureCreated()).isZero();
        assertThat(aliceRows()).hasSize(10);

        List<Transaction> lojaNovembro = aliceRows().stream()
                .filter(t -> t.getDescription().equals("LOJA X") && t.getDueDate().equals(NOV))
                .toList();
        assertThat(lojaNovembro).hasSize(1);
    }

    @Test
    void apagarDestaParcelaEmDiante_eTodas_ficamNaSerieENoUsuario() {
        imports.commit(alice, aliceCard, october());
        imports.commit(bob, bobCard, october());
        Transaction sixth = aliceRows().stream()
                .filter(t -> Short.valueOf((short) 6).equals(t.getInstallmentCurrent()))
                .findFirst().orElseThrow();

        transactionService.delete(alice, sixth.getId(), TransactionService.DeleteScope.FROM_HERE);
        transactions.flush();

        assertThat(aliceRows()).filteredOn(t -> "LOJA X".equals(t.getDescription()))
                .extracting(Transaction::getInstallmentCurrent)
                .containsExactlyInAnyOrder((short) 3, (short) 4, (short) 5);
        assertThat(aliceRows()).filteredOn(t -> "UBER".equals(t.getDescription())).hasSize(1);
        assertThat(bobRows()).hasSize(9);

        Transaction third = aliceRows().stream()
                .filter(t -> Short.valueOf((short) 3).equals(t.getInstallmentCurrent()))
                .findFirst().orElseThrow();
        transactionService.delete(alice, third.getId(), TransactionService.DeleteScope.ALL);
        transactions.flush();

        assertThat(aliceRows()).extracting(Transaction::getDescription).containsExactly("UBER");
        assertThat(bobRows()).hasSize(9);
    }

    @Test
    void serieDeOutroUsuario_naoEAlcancadaNemComIdForjado() {
        imports.commit(bob, bobCard, october());
        UUID bobSeries = bobRows().stream()
                .map(Transaction::getSeriesId).filter(s -> s != null).findFirst().orElseThrow();

        transactions.deleteByUserIdAndSeriesId(alice, bobSeries);
        transactions.flush();

        assertThat(bobRows()).hasSize(9);
        Transaction bobTx = bobRows().get(0);
        assertThatThrownBy(() -> transactionService.delete(alice, bobTx.getId(), TransactionService.DeleteScope.ALL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void importarNoCartaoDeOutroUsuario_da404() {
        assertThatThrownBy(() -> imports.commit(alice, bobCard, october()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(bobRows()).isEmpty();
    }

    // ── Apoio ────────────────────────────────────────────────────────────

    private List<Transaction> aliceRows() {
        return transactions.findAllByUserIdAndAccountIdAndDueDateGreaterThanEqual(alice, aliceCard, OCT.withDayOfMonth(1));
    }

    private List<Transaction> bobRows() {
        return transactions.findAllByUserIdAndAccountIdAndDueDateGreaterThanEqual(bob, bobCard, OCT.withDayOfMonth(1));
    }

    private static InvoiceImportForm october() {
        return form(OCT, item("LOJA X", "100.00", 3, 10), item("UBER", "15.90", null, null));
    }

    private static InvoiceImportForm form(LocalDate due, InvoiceItemForm... items) {
        InvoiceImportForm form = new InvoiceImportForm();
        form.setDueDate(due);
        form.setDueDateRead(true);
        form.getItems().addAll(List.of(items));
        return form;
    }

    private static InvoiceItemForm item(String description, String amount, Integer current, Integer total) {
        InvoiceItemForm item = new InvoiceItemForm();
        item.setDescription(description);
        item.setAmount(new BigDecimal(amount));
        item.setInstallment(current == null ? null : current.shortValue());
        item.setInstallments(total == null ? null : total.shortValue());
        item.setType(InvoiceLineType.PURCHASE);
        item.setCategoryId("outros");
        item.setSelected(true);
        return item;
    }

    private User user(String name) {
        User u = new User();
        u.setName(name);
        u.setEmail(name + "-" + UUID.randomUUID() + "@example.com");
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return users.saveAndFlush(u);
    }

    private Account card(UUID owner) {
        Account a = new Account();
        a.setUserId(owner);
        a.setName("Cartão teste");
        a.setKind(AccountKind.CARD);
        a.setDueDay((short) 10);
        return accounts.saveAndFlush(a);
    }
}

package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.AccountsView;
import com.rastroos.web.form.AccountForm;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountsRepo;
    @Mock private TransactionRepository txRepo;

    @InjectMocks private AccountService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob   = UUID.randomUUID();

    @Test
    void requireDeveLancarNotFoundQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(accountsRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(alice, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireRetornaApenasContaDoUsuarioCorreto() {
        UUID id = UUID.randomUUID();
        Account bobAccount = newAccount(bob, "Cartão do Bob", AccountKind.CARD);
        bobAccount.setId(id);
        // Alice tenta buscar conta do Bob → repo retorna empty
        when(accountsRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(alice, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createNaoDeixaUserIdSerSobrescritoPeloForm() {
        AccountForm form = new AccountForm();
        form.setName("Inter");
        form.setKind(AccountKind.CARD);
        form.setCloseDay((short) 5);
        form.setDueDay((short) 12);

        when(accountsRepo.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account created = service.create(alice, form);

        assertThat(created.getUserId()).isEqualTo(alice);
        assertThat(created.getName()).isEqualTo("Inter");
        assertThat(created.getKind()).isEqualTo(AccountKind.CARD);
    }

    @Test
    void createNormalizaCorParaIncluirCardinalSemHash() {
        AccountForm form = new AccountForm();
        form.setName("Inter");
        form.setKind(AccountKind.CARD);
        form.setColorHex("ff8800");

        when(accountsRepo.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account created = service.create(alice, form);
        assertThat(created.getColorHex()).isEqualTo("#ff8800");
    }

    @Test
    void createIgnoraDiasParaContaQueNaoEhCartao() {
        AccountForm form = new AccountForm();
        form.setName("Aluguel");
        form.setKind(AccountKind.BILL);
        form.setCloseDay((short) 5);
        form.setDueDay((short) 12);
        form.setFixed(true);

        when(accountsRepo.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account created = service.create(alice, form);
        assertThat(created.getCloseDay()).isNull();
        assertThat(created.getDueDay()).isNull();
        assertThat(created.isFixed()).isTrue();
    }

    @Test
    void deleteFalhaSeContaTemLançamentos() {
        UUID id = UUID.randomUUID();
        Account a = newAccount(alice, "X", AccountKind.CARD);
        a.setId(id);
        when(accountsRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(a));
        when(txRepo.countByUserIdAndAccountId(alice, id)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(alice, id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("account.hasTransactions");

        verify(accountsRepo, never()).delete(any(Account.class));
    }

    @Test
    void deleteRemoveQuandoNaoTemLancamentos() {
        UUID id = UUID.randomUUID();
        Account a = newAccount(alice, "X", AccountKind.CARD);
        a.setId(id);
        when(accountsRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(a));
        when(txRepo.countByUserIdAndAccountId(alice, id)).thenReturn(0L);

        service.delete(alice, id);
        verify(accountsRepo).delete(a);
    }

    @Test
    void listForMonthAgrupaPorTipoEAplicaAgregados() {
        YearMonth ym = YearMonth.of(2026, 5);

        Account card = newAccount(alice, "Cartão", AccountKind.CARD);
        card.setId(UUID.randomUUID());
        Account bill = newAccount(alice, "Aluguel", AccountKind.BILL);
        bill.setId(UUID.randomUUID());

        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice))
                .thenReturn(List.of(bill, card));

        when(txRepo.aggregateByAccountAndPeriod(eq(alice),
                                                eq(LocalDate.of(2026, 5, 1)),
                                                eq(LocalDate.of(2026, 6, 1))))
                .thenReturn(List.of(
                        new Object[] { card.getId(), 10_000L, 4_000L, 2L },
                        new Object[] { bill.getId(),  3_000L, 3_000L, 1L }
                ));

        AccountsView view = service.listForMonth(alice, ym);

        assertThat(view.cards()).hasSize(1);
        assertThat(view.cards().get(0).total().toPlainString()).isEqualTo("100.00");
        assertThat(view.cards().get(0).paidPercent()).isEqualTo(40);

        assertThat(view.bills()).hasSize(1);
        AccountSummaryDto billSummary = view.bills().get(0);
        assertThat(billSummary.total().toPlainString()).isEqualTo("30.00");
        assertThat(billSummary.paidPercent()).isEqualTo(100);
        assertThat(billSummary.isFullyPaid()).isTrue();
    }

    @Test
    void topAccountsForMonthDescartaContasSemMovimento() {
        YearMonth ym = YearMonth.of(2026, 5);

        Account com = newAccount(alice, "Com mov", AccountKind.CARD);
        com.setId(UUID.randomUUID());
        Account semMov = newAccount(alice, "Sem mov", AccountKind.CARD);
        semMov.setId(UUID.randomUUID());

        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice))
                .thenReturn(List.of(com, semMov));

        when(txRepo.aggregateByAccountAndPeriod(eq(alice), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { com.getId(), 5_000L, 1_000L, 1L }));

        List<AccountSummaryDto> top = service.topAccountsForMonth(alice, ym, 6);

        assertThat(top).hasSize(1);
        assertThat(top.get(0).id()).isEqualTo(com.getId());
    }

    private static Account newAccount(UUID userId, String name, AccountKind kind) {
        Account a = new Account();
        a.setUserId(userId);
        a.setName(name);
        a.setKind(kind);
        return a;
    }
}

package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.entity.InvestmentMovement;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.entity.enums.InvestmentMovementKind;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.InvestmentHistoryRepository;
import com.rastroos.domain.repository.InvestmentMovementRepository;
import com.rastroos.domain.repository.InvestmentRepository;
import com.rastroos.web.dto.InvestmentsView;
import com.rastroos.web.form.InvestmentForm;
import com.rastroos.web.form.InvestmentHistoryForm;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceTest {

    @Mock private InvestmentRepository invRepo;
    @Mock private InvestmentHistoryRepository historyRepo;
    @Mock private InvestmentMovementRepository movementRepo;

    @InjectMocks private InvestmentService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob   = UUID.randomUUID();

    @Test
    void loadSeparaPiggiesEPortfolioECalculaProgresso() {
        Investment piggy = newPiggy(alice, "Viagem", 200_00L, 1_000_00L);
        Investment cdb   = newPortfolio(alice, "CDB Banco X", 500_00L, 5_00L);

        when(invRepo.findAllByUserIdOrderByNameAsc(alice))
                .thenReturn(List.of(piggy, cdb));
        when(invRepo.sumTotalByUser(alice)).thenReturn(700_00L);
        when(invRepo.sumPiggyGoalsByUser(alice)).thenReturn(1_000_00L);
        when(invRepo.sumMonthlyReturnByUser(alice)).thenReturn(5_00L);
        when(invRepo.aggregateByKindAndUser(alice)).thenReturn(List.<Object[]>of(
                new Object[] { InvestmentKind.PIGGY, 200_00L },
                new Object[] { InvestmentKind.CDI,   500_00L }
        ));

        InvestmentsView view = service.load(alice);

        assertThat(view.piggies()).hasSize(1);
        assertThat(view.piggies().get(0).name()).isEqualTo("Viagem");
        assertThat(view.piggies().get(0).progressPercent()).isEqualTo(20);

        assertThat(view.portfolio()).hasSize(1);
        assertThat(view.portfolio().get(0).name()).isEqualTo("CDB Banco X");
        assertThat(view.portfolio().get(0).monthlyReturn().toPlainString()).isEqualTo("5.00");

        assertThat(view.summary().totalInvested().toPlainString()).isEqualTo("700.00");
        assertThat(view.summary().totalGoals().toPlainString()).isEqualTo("1000.00");
        assertThat(view.summary().piggyProgress()).isEqualTo(20);
        assertThat(view.summary().monthlyReturn().toPlainString()).isEqualTo("5.00");
        assertThat(view.summary().byKind()).containsKeys(InvestmentKind.PIGGY, InvestmentKind.CDI);
    }

    @Test
    void loadComSemMetasRetornaProgressNull() {
        Investment cdb = newPortfolio(alice, "CDB", 100_00L, null);
        when(invRepo.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of(cdb));
        when(invRepo.sumTotalByUser(alice)).thenReturn(100_00L);
        when(invRepo.sumPiggyGoalsByUser(alice)).thenReturn(0L);
        when(invRepo.sumMonthlyReturnByUser(alice)).thenReturn(0L);
        when(invRepo.aggregateByKindAndUser(alice)).thenReturn(List.of());

        InvestmentsView view = service.load(alice);

        assertThat(view.summary().piggyProgress()).isNull();
    }

    @Test
    void requireDeIdInexistenteLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(invRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(alice, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireDeOutroUsuarioLancaNotFound() {
        UUID id = UUID.randomUUID();
        // existe pra Bob, mas Alice chama
        when(invRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(alice, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createDePiggyZeraCamposDeRendimento() {
        InvestmentForm form = makeForm("Viagem", InvestmentKind.PIGGY,
                new BigDecimal("100.00"), new BigDecimal("1000.00"),
                "ignorado", new BigDecimal("99.99"));

        when(invRepo.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));

        Investment created = service.create(alice, form);

        assertThat(created.getUserId()).isEqualTo(alice);
        assertThat(created.getKind()).isEqualTo(InvestmentKind.PIGGY);
        assertThat(created.getGoalCents()).isEqualTo(100_000L);
        // Cofrinho agora também guarda taxa/rendimento (como no protótipo):
        assertThat(created.getRateLabel()).isEqualTo("ignorado");
        assertThat(created.getMonthlyReturnCents()).isEqualTo(9_999L);
    }

    @Test
    void createDeCdbZeraMetaECopiaRateMonthly() {
        InvestmentForm form = makeForm("CDB", InvestmentKind.CDI,
                new BigDecimal("500.00"), new BigDecimal("1000.00"),
                "105% CDI", new BigDecimal("4.50"));

        when(invRepo.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));

        Investment created = service.create(alice, form);

        assertThat(created.getKind()).isEqualTo(InvestmentKind.CDI);
        assertThat(created.getGoalCents()).isNull();
        assertThat(created.getRateLabel()).isEqualTo("105% CDI");
        assertThat(created.getMonthlyReturnCents()).isEqualTo(450L);
    }

    @Test
    void createComSaldoInicialRegistraAporteNoHistorico() {
        InvestmentForm form = makeForm("CDB", InvestmentKind.CDI,
                new BigDecimal("500.00"), new BigDecimal("1000.00"),
                "105% CDI", new BigDecimal("4.50"));
        when(invRepo.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepo.save(any(InvestmentHistory.class))).thenAnswer(c -> c.getArgument(0));

        service.create(alice, form);

        ArgumentCaptor<InvestmentHistory> cap = ArgumentCaptor.forClass(InvestmentHistory.class);
        verify(historyRepo).save(cap.capture());
        assertThat(cap.getValue().getAmountCents()).isEqualTo(50_000L);
        assertThat(cap.getValue().getContributedCents()).isEqualTo(50_000L);

        ArgumentCaptor<InvestmentMovement> mov = ArgumentCaptor.forClass(InvestmentMovement.class);
        verify(movementRepo).save(mov.capture());
        assertThat(mov.getValue().getKind()).isEqualTo(InvestmentMovementKind.INITIAL);
        assertThat(mov.getValue().getAmountCents()).isEqualTo(50_000L);
        assertThat(mov.getValue().getBalanceAfterCents()).isEqualTo(50_000L);
    }

    @Test
    void depositRegistraDinheiroNovoComoContributedCents() {
        UUID id = UUID.randomUUID();
        Investment inv = newPortfolio(alice, "X", 100_00L, 0L);
        inv.setId(id);
        when(invRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(inv));
        when(invRepo.save(any(Investment.class))).thenAnswer(c -> c.getArgument(0));
        when(historyRepo.save(any(InvestmentHistory.class))).thenAnswer(c -> c.getArgument(0));
        when(historyRepo.findByInvestmentIdAndYearMonth(any(), any())).thenReturn(Optional.empty());

        service.deposit(alice, id, new BigDecimal("50.00"));

        ArgumentCaptor<InvestmentHistory> cap = ArgumentCaptor.forClass(InvestmentHistory.class);
        verify(historyRepo).save(cap.capture());
        assertThat(cap.getValue().getAmountCents()).isEqualTo(150_00L);      // 100 + 50
        assertThat(cap.getValue().getContributedCents()).isEqualTo(50_00L);  // dinheiro novo do mês

        ArgumentCaptor<InvestmentMovement> mov = ArgumentCaptor.forClass(InvestmentMovement.class);
        verify(movementRepo).save(mov.capture());
        assertThat(mov.getValue().getKind()).isEqualTo(InvestmentMovementKind.DEPOSIT);
        assertThat(mov.getValue().getAmountCents()).isEqualTo(50_00L);       // valor do aporte
        assertThat(mov.getValue().getBalanceAfterCents()).isEqualTo(150_00L);
    }

    @Test
    void deleteRemoveTambemHistoricoOrfaos() {
        UUID id = UUID.randomUUID();
        Investment inv = newPortfolio(alice, "X", 100_00L, 0L);
        inv.setId(id);
        when(invRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(inv));

        InvestmentHistory h1 = new InvestmentHistory();
        h1.setInvestmentId(id);
        h1.setYearMonth("2026-04");
        h1.setAmountCents(90_00L);
        InvestmentHistory h2 = new InvestmentHistory();
        h2.setInvestmentId(id);
        h2.setYearMonth("2026-05");
        h2.setAmountCents(100_00L);
        when(historyRepo.findAllByInvestmentIdOrderByYearMonthAsc(id))
                .thenReturn(List.of(h1, h2));

        service.delete(alice, id);

        verify(historyRepo).deleteAll(List.of(h1, h2));
        verify(movementRepo).deleteByInvestmentId(id);
        verify(invRepo).delete(inv);
    }

    @Test
    void deleteDeIdDeOutroUsuarioLancaNotFoundENaoApaga() {
        UUID id = UUID.randomUUID();
        when(invRepo.findByIdAndUserId(id, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(bob, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(invRepo, never()).delete(any(Investment.class));
        verify(historyRepo, never()).deleteAll(any());
    }

    @Test
    void upsertHistoryCriaNovoQuandoNaoExisteEAtualizaAmountDoInvestment() {
        UUID id = UUID.randomUUID();
        Investment inv = newPortfolio(alice, "X", 100_00L, 0L);
        inv.setId(id);
        when(invRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(inv));
        when(historyRepo.findByInvestmentIdAndYearMonth(id, "2026-05"))
                .thenReturn(Optional.empty());
        when(historyRepo.save(any(InvestmentHistory.class)))
                .thenAnswer(invc -> invc.getArgument(0));
        when(invRepo.save(any(Investment.class))).thenAnswer(invc -> invc.getArgument(0));

        InvestmentHistoryForm form = new InvestmentHistoryForm();
        form.setYearMonth("2026-05");
        form.setAmount(new BigDecimal("123.45"));

        InvestmentHistory saved = service.upsertHistory(alice, id, form);

        assertThat(saved.getInvestmentId()).isEqualTo(id);
        assertThat(saved.getYearMonth()).isEqualTo("2026-05");
        assertThat(saved.getAmountCents()).isEqualTo(12_345L);

        // Atualiza amount do investment
        ArgumentCaptor<Investment> cap = ArgumentCaptor.forClass(Investment.class);
        verify(invRepo).save(cap.capture());
        assertThat(cap.getValue().getAmountCents()).isEqualTo(12_345L);
    }

    @Test
    void upsertHistoryAtualizaExistente() {
        UUID id = UUID.randomUUID();
        Investment inv = newPortfolio(alice, "X", 100_00L, 0L);
        inv.setId(id);
        InvestmentHistory existing = new InvestmentHistory();
        existing.setId(99L);
        existing.setInvestmentId(id);
        existing.setYearMonth("2026-05");
        existing.setAmountCents(50_00L);

        when(invRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(inv));
        when(historyRepo.findByInvestmentIdAndYearMonth(id, "2026-05"))
                .thenReturn(Optional.of(existing));
        when(historyRepo.save(any(InvestmentHistory.class)))
                .thenAnswer(invc -> invc.getArgument(0));
        when(invRepo.save(any(Investment.class))).thenAnswer(invc -> invc.getArgument(0));

        InvestmentHistoryForm form = new InvestmentHistoryForm();
        form.setYearMonth("2026-05");
        form.setAmount(new BigDecimal("200.00"));

        InvestmentHistory saved = service.upsertHistory(alice, id, form);

        assertThat(saved.getId()).isEqualTo(99L);
        assertThat(saved.getAmountCents()).isEqualTo(20_000L);
    }

    @Test
    void getHistoryParaIdDeOutroUserLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(invRepo.findByIdAndUserId(id, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(bob, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ──────────────────────────────────────────────

    private InvestmentForm makeForm(String name, InvestmentKind kind,
                                    BigDecimal amount, BigDecimal goal,
                                    String rate, BigDecimal monthly) {
        InvestmentForm f = new InvestmentForm();
        f.setName(name);
        f.setKind(kind);
        f.setAmount(amount);
        f.setGoal(goal);
        f.setRateLabel(rate);
        f.setMonthlyReturn(monthly);
        return f;
    }

    private Investment newPiggy(UUID userId, String name,
                                long amountCents, long goalCents) {
        Investment i = new Investment();
        i.setUserId(userId);
        i.setName(name);
        i.setKind(InvestmentKind.PIGGY);
        i.setAmountCents(amountCents);
        i.setGoalCents(goalCents);
        return i;
    }

    private Investment newPortfolio(UUID userId, String name,
                                    long amountCents, Long monthlyReturnCents) {
        Investment i = new Investment();
        i.setUserId(userId);
        i.setName(name);
        i.setKind(InvestmentKind.CDI);
        i.setAmountCents(amountCents);
        i.setRateLabel("100% CDI");
        i.setMonthlyReturnCents(monthlyReturnCents);
        return i;
    }
}

package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.InvestmentHistory;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.InvestmentKind;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Cobre as queries agregadas de {@link InvestmentRepository} com Postgres
 * real: somas, agregação por kind, history upsert via combo
 * (investmentId, yearMonth). Isolamento por user é central.
 */
class InvestmentRepositoryAggregateTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private InvestmentRepository investments;
    @Autowired private InvestmentHistoryRepository history;

    @Test
    void somasTotalGoalsEMonthlyReturnNaoVazamEntreUsuarios() {
        User alice = users.saveAndFlush(newUser("a-inv@example.com"));
        User bob   = users.saveAndFlush(newUser("b-inv@example.com"));

        savePiggy(alice, "Viagem",  200_00L, 1_000_00L);
        saveCdb(alice,   "CDB BB",  500_00L, 4_00L);
        // Bob — não pode aparecer
        savePiggy(bob,   "Bob piggy", 999_00L, 9_999_00L);

        long total = investments.sumTotalByUser(alice.getId());
        long goals = investments.sumPiggyGoalsByUser(alice.getId());
        long monthlyReturn = investments.sumMonthlyReturnByUser(alice.getId());

        assertThat(total).isEqualTo(700_00L);
        assertThat(goals).isEqualTo(1_000_00L);
        assertThat(monthlyReturn).isEqualTo(4_00L);
    }

    @Test
    void aggregateByKindRetornaUmaLinhaPorTipoSomenteDoUser() {
        User alice = users.saveAndFlush(newUser("a-inv-k@example.com"));
        User bob   = users.saveAndFlush(newUser("b-inv-k@example.com"));

        savePiggy(alice, "Viagem", 200_00L, null);
        savePiggy(alice, "PC",     100_00L, null);
        saveCdb(alice,   "CDB",    500_00L, 5_00L);
        // Bob
        savePiggy(bob,   "Bob",    999_00L, null);

        List<Object[]> rows = investments.aggregateByKindAndUser(alice.getId());

        Map<InvestmentKind, Long> byKind = rows.stream().collect(Collectors.toMap(
                r -> (InvestmentKind) r[0],
                r -> ((Number) r[1]).longValue()));

        assertThat(byKind).containsEntry(InvestmentKind.PIGGY, 300_00L);
        assertThat(byKind).containsEntry(InvestmentKind.CDI,   500_00L);
        assertThat(byKind).doesNotContainKey(InvestmentKind.STOCK);
    }

    @Test
    void findByIdAndUserIdDeOutroUsuarioRetornaEmpty() {
        User alice = users.saveAndFlush(newUser("a-inv-iso@example.com"));
        User bob   = users.saveAndFlush(newUser("b-inv-iso@example.com"));

        Investment aliceInv = savePiggy(alice, "Viagem", 100_00L, null);

        // Bob tenta acessar id da Alice
        assertThat(investments.findByIdAndUserId(aliceInv.getId(), bob.getId())).isEmpty();
        assertThat(investments.findByIdAndUserId(aliceInv.getId(), alice.getId())).isPresent();
    }

    @Test
    void historyUpsertViaFindByInvestmentIdAndYearMonth() {
        User alice = users.saveAndFlush(newUser("a-inv-hist@example.com"));
        Investment cdb = saveCdb(alice, "CDB", 100_00L, null);

        InvestmentHistory h = new InvestmentHistory();
        h.setInvestmentId(cdb.getId());
        h.setYearMonth("2026-05");
        h.setAmountCents(110_00L);
        history.saveAndFlush(h);

        assertThat(history.findByInvestmentIdAndYearMonth(cdb.getId(), "2026-05"))
                .isPresent()
                .get()
                .extracting(InvestmentHistory::getAmountCents)
                .isEqualTo(110_00L);

        // Outro mês
        assertThat(history.findByInvestmentIdAndYearMonth(cdb.getId(), "2026-06"))
                .isEmpty();
    }

    @Test
    void historyOrdenadoPorYearMonth() {
        User alice = users.saveAndFlush(newUser("a-inv-h2@example.com"));
        Investment cdb = saveCdb(alice, "CDB", 100_00L, null);

        history.saveAndFlush(historyAt(cdb.getId(), "2026-06", 110_00L));
        history.saveAndFlush(historyAt(cdb.getId(), "2026-04", 100_00L));
        history.saveAndFlush(historyAt(cdb.getId(), "2026-05", 105_00L));

        List<InvestmentHistory> all = history
                .findAllByInvestmentIdOrderByYearMonthAsc(cdb.getId());

        assertThat(all).extracting(InvestmentHistory::getYearMonth)
                .containsExactly("2026-04", "2026-05", "2026-06");
    }

    // ── helpers ──────────────────────────────────────────────

    private static User newUser(String email) {
        User u = new User();
        u.setName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private Investment savePiggy(User user, String name, long amountCents, Long goalCents) {
        Investment i = new Investment();
        i.setUserId(user.getId());
        i.setName(name);
        i.setKind(InvestmentKind.PIGGY);
        i.setAmountCents(amountCents);
        i.setGoalCents(goalCents);
        return investments.saveAndFlush(i);
    }

    private Investment saveCdb(User user, String name, long amountCents, Long monthlyReturnCents) {
        Investment i = new Investment();
        i.setUserId(user.getId());
        i.setName(name);
        i.setKind(InvestmentKind.CDI);
        i.setAmountCents(amountCents);
        i.setRateLabel("100% CDI");
        i.setMonthlyReturnCents(monthlyReturnCents);
        return investments.saveAndFlush(i);
    }

    private static InvestmentHistory historyAt(UUID investmentId, String yearMonth, long amountCents) {
        InvestmentHistory h = new InvestmentHistory();
        h.setInvestmentId(investmentId);
        h.setYearMonth(yearMonth);
        h.setAmountCents(amountCents);
        return h;
    }
}

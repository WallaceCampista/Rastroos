package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Cobre {@link IncomeRepository#searchByFilters} e
 * {@link IncomeRepository#totalByFilters} contra Postgres real.
 *
 * <p>Garante isolamento por user, filtro por categoria e busca
 * case-insensitive em {@code source}.
 */
class IncomeFilterRepositoryTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private IncomeRepository incomes;

    @Test
    void searchSemFiltrosListaTodosNoMesEDoUsuario() {
        User alice = users.saveAndFlush(newUser("a-i-search@example.com"));
        User bob   = users.saveAndFlush(newUser("b-i-search@example.com"));

        save(alice, "Salário",       300_000L, LocalDate.of(2026, 5, 5),  "outros");
        save(alice, "Freela",        100_000L, LocalDate.of(2026, 5, 15), "outros");
        save(bob,   "Salário Bob",   999_000L, LocalDate.of(2026, 5, 5),  "outros");
        // fora do período
        save(alice, "Mês anterior",   50_000L, LocalDate.of(2026, 4, 30), "outros");

        Page<Income> result = incomes.searchByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, "",
                pageable(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Income::getSource)
                .containsExactlyInAnyOrder("Salário", "Freela");
    }

    @Test
    void searchComCategoriaRetornaSomenteDaCategoria() {
        User alice = users.saveAndFlush(newUser("a-i-cat@example.com"));

        save(alice, "Salário", 300_000L, LocalDate.of(2026, 5, 5),  "outros");
        save(alice, "Aluguel",  80_000L, LocalDate.of(2026, 5, 10), "moradia");

        Page<Income> result = incomes.searchByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                "moradia", "",
                pageable(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSource()).isEqualTo("Aluguel");
    }

    @Test
    void searchComBuscaCaseInsensitive() {
        User alice = users.saveAndFlush(newUser("a-i-q@example.com"));

        save(alice, "Empresa XYZ",     500_000L, LocalDate.of(2026, 5, 1),  "outros");
        save(alice, "Cliente XPTO",    100_000L, LocalDate.of(2026, 5, 10), "outros");

        Page<Income> result = incomes.searchByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, "EMPRESA",
                pageable(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSource()).startsWith("Empresa");
    }

    @Test
    void totalByFiltersAplicaMesmosCriteriosERetornaSoma() {
        User alice = users.saveAndFlush(newUser("a-i-tot@example.com"));

        save(alice, "A",  100_000L, LocalDate.of(2026, 5, 5),  "outros");
        save(alice, "B",  200_000L, LocalDate.of(2026, 5, 8),  "outros");
        save(alice, "C",  300_000L, LocalDate.of(2026, 5, 12), "moradia");

        long todos = incomes.totalByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, "");
        assertThat(todos).isEqualTo(600_000L);

        long apenasOutros = incomes.totalByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                "outros", "");
        assertThat(apenasOutros).isEqualTo(300_000L);
    }

    @Test
    void isolamentoPorUserNaoVazaTotaisDoOutro() {
        User alice = users.saveAndFlush(newUser("a-i-iso@example.com"));
        User bob   = users.saveAndFlush(newUser("b-i-iso@example.com"));

        save(alice, "Alice 1", 100_000L, LocalDate.of(2026, 5, 3), "outros");
        save(bob,   "Bob 1",   999_000L, LocalDate.of(2026, 5, 3), "outros");

        long aliceTotal = incomes.totalByFilters(
                alice.getId(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                null, "");
        assertThat(aliceTotal).isEqualTo(100_000L);
    }

    // ── helpers ──────────────────────────────────────────────

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "incomeDate").and(Sort.by("createdAt")));
    }

    private static User newUser(String email) {
        User u = new User();
        u.setName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPasswordHash("$2a$12$" + "x".repeat(53));
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private void save(User user, String source, long amountCents,
                      LocalDate date, String category) {
        Income i = new Income();
        i.setUserId(user.getId());
        i.setSource(source);
        i.setAmountCents(amountCents);
        i.setIncomeDate(date);
        i.setCategory(category);
        incomes.saveAndFlush(i);
    }

    @SuppressWarnings("unused")
    private UUID dummy() { return UUID.randomUUID(); }
}

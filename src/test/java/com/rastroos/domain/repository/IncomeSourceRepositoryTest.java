package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.IncomeSource;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

/**
 * Receita recorrente é dado pessoal: usuário A nunca alcança a fonte nem os
 * recebimentos do usuário B, e o corte "deste mês em diante" só toca as
 * linhas do dono.
 */
class IncomeSourceRepositoryTest extends RepositoryTestBase {

    @Autowired private UserRepository users;
    @Autowired private IncomeSourceRepository sources;
    @Autowired private IncomeRepository incomes;

    @Test
    void fonteDeOutroUsuarioNuncaEEncontradaPeloFindByIdAndUserId() {
        User alice = users.saveAndFlush(newUser("alice-src@example.com"));
        User bob   = users.saveAndFlush(newUser("bob-src@example.com"));

        IncomeSource s = sources.saveAndFlush(newSource(alice, "Acme"));

        assertThat(sources.findByIdAndUserId(s.getId(), alice.getId())).isPresent();
        assertThat(sources.findByIdAndUserId(s.getId(), bob.getId())).isEmpty();
    }

    @Test
    void listagemDeAtivasIgnoraAsEncerradasEAsDeOutroUsuario() {
        User alice = users.saveAndFlush(newUser("alice-act@example.com"));
        User bob   = users.saveAndFlush(newUser("bob-act@example.com"));

        sources.saveAndFlush(newSource(alice, "Ativa"));
        IncomeSource closed = newSource(alice, "Encerrada");
        closed.setClosedAt(LocalDate.of(2026, 4, 30));
        sources.saveAndFlush(closed);
        sources.saveAndFlush(newSource(bob, "Do Bob"));

        List<IncomeSource> ativas =
                sources.findAllByUserIdAndClosedAtIsNullOrderByNameAsc(alice.getId());

        assertThat(ativas).extracting(IncomeSource::getName).containsExactly("Ativa");
        assertThat(sources.findAllByUserIdOrderByNameAsc(alice.getId()))
                .extracting(IncomeSource::getName).containsExactly("Ativa", "Encerrada");
    }

    @Test
    void corteDeleteDoMesEmDianteApagaSoAsLinhasDoDonoEDoPeriodo() {
        User alice = users.saveAndFlush(newUser("alice-cut@example.com"));
        User bob   = users.saveAndFlush(newUser("bob-cut@example.com"));

        IncomeSource aliceSrc = sources.saveAndFlush(newSource(alice, "Acme"));
        IncomeSource bobSrc   = sources.saveAndFlush(newSource(bob, "Acme"));

        incomes.saveAndFlush(newIncome(alice, aliceSrc, LocalDate.of(2026, 4, 5)));
        incomes.saveAndFlush(newIncome(alice, aliceSrc, LocalDate.of(2026, 5, 5)));
        incomes.saveAndFlush(newIncome(alice, aliceSrc, LocalDate.of(2026, 6, 5)));
        incomes.saveAndFlush(newIncome(bob, bobSrc, LocalDate.of(2026, 6, 5)));

        assertThat(incomes.countByUserIdAndSourceId(alice.getId(), aliceSrc.getId())).isEqualTo(3);
        assertThat(incomes.countByUserIdAndSourceIdAndIncomeDateGreaterThanEqual(
                alice.getId(), aliceSrc.getId(), LocalDate.of(2026, 5, 1))).isEqualTo(2);

        incomes.deleteByUserIdAndSourceIdAndIncomeDateGreaterThanEqual(
                alice.getId(), aliceSrc.getId(), LocalDate.of(2026, 5, 1));
        incomes.flush();

        assertThat(incomes.countByUserIdAndSourceId(alice.getId(), aliceSrc.getId())).isEqualTo(1);
        // O recebimento do Bob no mesmo mês não foi tocado.
        assertThat(incomes.countByUserIdAndSourceId(bob.getId(), bobSrc.getId())).isEqualTo(1);
    }

    @Test
    void somaDeRecebidoIgnoraOProgramadoENaoCruzaUsuarios() {
        User alice = users.saveAndFlush(newUser("alice-rec@example.com"));
        User bob   = users.saveAndFlush(newUser("bob-rec@example.com"));

        IncomeSource aliceSrc = sources.saveAndFlush(newSource(alice, "Acme"));
        IncomeSource bobSrc   = sources.saveAndFlush(newSource(bob, "Acme"));

        Income confirmado = newIncome(alice, aliceSrc, LocalDate.of(2026, 5, 8));
        confirmado.setReceived(true);
        incomes.saveAndFlush(confirmado);
        // Programado e não confirmado: entra na previsão, não no recebido.
        incomes.saveAndFlush(newIncome(alice, aliceSrc, LocalDate.of(2026, 5, 20)));
        Income doBob = newIncome(bob, bobSrc, LocalDate.of(2026, 5, 8));
        doBob.setReceived(true);
        incomes.saveAndFlush(doBob);

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 6, 1);

        assertThat(incomes.sumAmountByUserAndPeriod(alice.getId(), start, end))
                .isEqualTo(1_000_000L);
        assertThat(incomes.sumReceivedByUserAndPeriod(alice.getId(), start, end))
                .isEqualTo(500_000L);
        // O recebimento confirmado do Bob não vaza para a soma da Alice.
        assertThat(incomes.sumReceivedByUserAndPeriod(bob.getId(), start, end))
                .isEqualTo(500_000L);
    }

    @Test
    void ocorrenciasDoMesTrazemUmaLinhaPorFonteDoDono() {
        User alice = users.saveAndFlush(newUser("alice-occ@example.com"));
        User bob   = users.saveAndFlush(newUser("bob-occ@example.com"));

        IncomeSource acme = sources.saveAndFlush(newSource(alice, "Acme"));
        IncomeSource rio  = sources.saveAndFlush(newSource(alice, "Rio"));
        IncomeSource doBob = sources.saveAndFlush(newSource(bob, "Acme"));

        incomes.saveAndFlush(newIncome(alice, acme, LocalDate.of(2026, 5, 8)));
        incomes.saveAndFlush(newIncome(alice, rio, LocalDate.of(2026, 5, 15)));
        incomes.saveAndFlush(newIncome(bob, doBob, LocalDate.of(2026, 5, 8)));
        // Avulso não tem fonte: fica de fora.
        Income avulso = newIncome(alice, acme, LocalDate.of(2026, 5, 20));
        avulso.setSourceId(null);
        incomes.saveAndFlush(avulso);

        List<Income> occurrences = incomes.findSourceOccurrencesInPeriod(
                alice.getId(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));

        assertThat(occurrences).hasSize(2)
                .extracting(Income::getSourceId)
                .containsExactlyInAnyOrder(acme.getId(), rio.getId());
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

    private static IncomeSource newSource(User u, String name) {
        IncomeSource s = new IncomeSource();
        s.setUserId(u.getId());
        s.setName(name);
        s.setAmountCents(500_000L);
        s.setPayBusinessDay((short) 5);
        return s;
    }

    private static Income newIncome(User u, IncomeSource s, LocalDate date) {
        Income i = new Income();
        i.setUserId(u.getId());
        i.setSourceId(s.getId());
        i.setSource(s.getName());
        i.setAmountCents(s.getAmountCents());
        i.setIncomeDate(date);
        return i;
    }
}

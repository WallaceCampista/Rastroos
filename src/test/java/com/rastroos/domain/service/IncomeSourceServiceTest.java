package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.IncomeSource;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.IncomeSourceRepository;
import com.rastroos.web.form.IncomeSourceForm;

@ExtendWith(MockitoExtension.class)
class IncomeSourceServiceTest {

    @Mock private IncomeSourceRepository sourcesRepo;
    @Mock private IncomeRepository incomesRepo;
    @Mock private ApplicationEventPublisher events;

    @Captor private ArgumentCaptor<List<Income>> savedIncomes;

    private final Clock clock =
            Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneId.of("UTC"));

    private final UUID alice = UUID.randomUUID();
    private final UUID bob   = UUID.randomUUID();

    private IncomeSourceService service() {
        return new IncomeSourceService(sourcesRepo, incomesRepo, clock, events);
    }

    @Test
    void createMaterializaUmRecebimentoPorMesNoHorizonte() {
        IncomeSourceForm form = form("Acme Ltda", "5000.00", (short) 5, YearMonth.of(2026, 5));
        when(sourcesRepo.existsByUserIdAndNameIgnoreCase(alice, "Acme Ltda")).thenReturn(false);
        when(sourcesRepo.save(any(IncomeSource.class))).thenAnswer(inv -> {
            IncomeSource s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        IncomeSource created = service().create(alice, form);

        verify(incomesRepo).saveAll(savedIncomes.capture());
        List<Income> rows = new ArrayList<>(savedIncomes.getValue());

        assertThat(rows).hasSize(IncomeSourceService.HORIZON_MONTHS);
        assertThat(rows).allSatisfy(i -> {
            assertThat(i.getUserId()).isEqualTo(alice);
            assertThat(i.getSourceId()).isEqualTo(created.getId());
            assertThat(i.getSource()).isEqualTo("Acme Ltda");
            assertThat(i.getAmountCents()).isEqualTo(500_000L);
            assertThat(i.getCategory()).isNull();
        });
        // 5º dia útil, não dia 5: em maio/2026 o dia 1 é feriado e cai numa
        // sexta, então o 5º dia útil é 08/05; em junho, 08/06.
        assertThat(rows.get(0).getIncomeDate()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(rows.get(1).getIncomeDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        // Programado não é recebido.
        assertThat(rows).allSatisfy(i -> assertThat(i.isReceived()).isFalse());
    }

    @Test
    void createSemMesInicialComecaNoMesCorrenteDoClock() {
        IncomeSourceForm form = form("Acme", "1000.00", (short) 10, null);
        when(sourcesRepo.existsByUserIdAndNameIgnoreCase(alice, "Acme")).thenReturn(false);
        when(sourcesRepo.save(any(IncomeSource.class))).thenAnswer(inv -> {
            IncomeSource s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        service().create(alice, form);

        verify(incomesRepo).saveAll(savedIncomes.capture());
        assertThat(savedIncomes.getValue().get(0).getIncomeDate())
                .isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void diaUtilAlemDoQueOMesTemCaiNoUltimoDiaUtil() {
        // Fevereiro/2026 tem só 18 dias úteis (Carnaval); quem recebe no 20º
        // recebe no último dia útil, 27/02 — não some do mês.
        IncomeSourceForm form = form("Acme", "100.00", (short) 20, YearMonth.of(2026, 2));
        when(sourcesRepo.existsByUserIdAndNameIgnoreCase(alice, "Acme")).thenReturn(false);
        when(sourcesRepo.save(any(IncomeSource.class))).thenAnswer(inv -> {
            IncomeSource s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        service().create(alice, form);

        verify(incomesRepo).saveAll(savedIncomes.capture());
        assertThat(savedIncomes.getValue().get(0).getIncomeDate())
                .isEqualTo(LocalDate.of(2026, 2, 27));
    }

    @Test
    void createComNomeRepetidoRecusa() {
        IncomeSourceForm form = form("Acme", "100.00", (short) 5, null);
        when(sourcesRepo.existsByUserIdAndNameIgnoreCase(alice, "Acme")).thenReturn(true);

        assertThatThrownBy(() -> service().create(alice, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("incomeSource.duplicateName");
        verify(sourcesRepo, never()).save(any(IncomeSource.class));
    }

    @Test
    void createComValorZeradoRecusa() {
        IncomeSourceForm form = form("Acme", "0.00", (short) 5, null);
        when(sourcesRepo.existsByUserIdAndNameIgnoreCase(alice, "Acme")).thenReturn(false);

        assertThatThrownBy(() -> service().create(alice, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("income.amountPositive");
    }

    @Test
    void requireDeOutroUsuarioLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(sourcesRepo.findByIdAndUserId(id, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().require(bob, id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("incomeSource.notFound");
    }

    @Test
    void updateReescreveSoOsRecebimentosDoMesCorrenteEmDiante() {
        UUID id = UUID.randomUUID();
        IncomeSource existing = source(id, alice, "Acme", 500_000L, (short) 5);

        Income future = new Income();
        future.setUserId(alice);
        future.setSourceId(id);
        future.setSource("Acme");
        future.setAmountCents(500_000L);
        future.setIncomeDate(LocalDate.of(2026, 7, 5));

        when(sourcesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(existing));
        when(sourcesRepo.existsByUserIdAndNameIgnoreCaseAndIdNot(alice, "Acme S.A.", id))
                .thenReturn(false);
        when(sourcesRepo.save(any(IncomeSource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(incomesRepo.findAllByUserIdAndSourceIdAndIncomeDateGreaterThanEqualOrderByIncomeDateAsc(
                alice, id, LocalDate.of(2026, 5, 1))).thenReturn(List.of(future));

        service().update(alice, id, form("Acme S.A.", "5500.00", (short) 5, null));

        assertThat(future.getSource()).isEqualTo("Acme S.A.");
        assertThat(future.getAmountCents()).isEqualTo(550_000L);
        // 5º dia útil de julho/2026 = 07/07.
        assertThat(future.getIncomeDate()).isEqualTo(LocalDate.of(2026, 7, 7));
    }

    @Test
    void updateNaoReescreveRecebimentoJaConfirmado() {
        UUID id = UUID.randomUUID();
        IncomeSource existing = source(id, alice, "Acme", 500_000L, (short) 5);

        Income confirmado = new Income();
        confirmado.setUserId(alice);
        confirmado.setSourceId(id);
        confirmado.setSource("Acme");
        confirmado.setAmountCents(500_000L);
        confirmado.setIncomeDate(LocalDate.of(2026, 5, 8));
        confirmado.setReceived(true);

        when(sourcesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(existing));
        when(sourcesRepo.existsByUserIdAndNameIgnoreCaseAndIdNot(alice, "Acme", id))
                .thenReturn(false);
        when(sourcesRepo.save(any(IncomeSource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(incomesRepo.findAllByUserIdAndSourceIdAndIncomeDateGreaterThanEqualOrderByIncomeDateAsc(
                alice, id, LocalDate.of(2026, 5, 1))).thenReturn(List.of(confirmado));

        service().update(alice, id, form("Acme", "9000.00", (short) 1, null));

        // O depósito que já caiu é fato: valor e data ficam como estavam.
        assertThat(confirmado.getAmountCents()).isEqualTo(500_000L);
        assertThat(confirmado.getIncomeDate()).isEqualTo(LocalDate.of(2026, 5, 8));
    }

    @Test
    void deleteAllApagaRecebimentosEAFonte() {
        UUID id = UUID.randomUUID();
        IncomeSource existing = source(id, alice, "Acme", 500_000L, (short) 5);
        when(sourcesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(existing));

        service().delete(alice, id, IncomeSourceService.DeleteScope.ALL, null);

        verify(incomesRepo).deleteByUserIdAndSourceId(alice, id);
        verify(sourcesRepo).delete(existing);
    }

    @Test
    void deleteFromMonthApagaSoDoMesEmDianteEEncerraAFonte() {
        UUID id = UUID.randomUUID();
        IncomeSource existing = source(id, alice, "Acme", 500_000L, (short) 5);
        when(sourcesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(existing));
        when(sourcesRepo.save(any(IncomeSource.class))).thenAnswer(inv -> inv.getArgument(0));

        service().delete(alice, id, IncomeSourceService.DeleteScope.FROM_MONTH,
                YearMonth.of(2026, 6));

        verify(incomesRepo).deleteByUserIdAndSourceIdAndIncomeDateGreaterThanEqual(
                eq(alice), eq(id), eq(LocalDate.of(2026, 6, 1)));
        verify(sourcesRepo, never()).delete(any(IncomeSource.class));
        // Encerrada no fim do mês anterior: o histórico continua consultável.
        assertThat(existing.getClosedAt()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void deleteFromMonthSemMesRecusa() {
        UUID id = UUID.randomUUID();
        when(sourcesRepo.findByIdAndUserId(id, alice))
                .thenReturn(Optional.of(source(id, alice, "Acme", 100L, (short) 5)));

        assertThatThrownBy(() -> service()
                .delete(alice, id, IncomeSourceService.DeleteScope.FROM_MONTH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("incomeSource.deleteScopeInvalid");
    }

    @Test
    void deleteDeFonteDeOutroUsuarioLancaNotFoundENaoApagaNada() {
        UUID id = UUID.randomUUID();
        when(sourcesRepo.findByIdAndUserId(id, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service()
                .delete(bob, id, IncomeSourceService.DeleteScope.ALL, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(incomesRepo, never()).deleteByUserIdAndSourceId(any(), any());
        verify(sourcesRepo, never()).delete(any(IncomeSource.class));
    }

    // ── helpers ──────────────────────────────────────────────

    private static IncomeSourceForm form(String name, String amount,
                                         short payBusinessDay, YearMonth start) {
        IncomeSourceForm f = new IncomeSourceForm();
        f.setName(name);
        f.setAmount(new BigDecimal(amount));
        f.setPayBusinessDay(payBusinessDay);
        f.setStartMonth(start);
        return f;
    }

    private static IncomeSource source(UUID id, UUID userId, String name,
                                       long cents, short payBusinessDay) {
        IncomeSource s = new IncomeSource();
        s.setId(id);
        s.setUserId(userId);
        s.setName(name);
        s.setAmountCents(cents);
        s.setPayBusinessDay(payBusinessDay);
        return s;
    }
}

package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.IncomeSource;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.IncomeSourceRepository;
import com.rastroos.web.form.IncomeForm;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

    @Mock private IncomeRepository incomesRepo;
    @Mock private IncomeSourceRepository sourcesRepo;
    @Mock private CategoryRepository categoriesRepo;

    /** O evento de mudança de dados tem cobertura própria em
     *  UserDataVersionServiceTest; aqui basta não ser nulo. */
    @Mock private ApplicationEventPublisher events;

    private static final Instant NOW = Instant.parse("2026-05-15T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    private IncomeService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob   = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new IncomeService(incomesRepo, sourcesRepo, categoriesRepo, clock, events);
    }

    @Test
    void createConverteAmountParaCentavosCorretamente() {
        IncomeForm form = makeForm("Salário", new BigDecimal("3500.00"),
                LocalDate.of(2026, 5, 5), null);
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income created = service.create(alice, form);

        assertThat(created.getUserId()).isEqualTo(alice);
        assertThat(created.getSource()).isEqualTo("Salário");
        assertThat(created.getAmountCents()).isEqualTo(350_000L);
        assertThat(created.getIncomeDate()).isEqualTo(LocalDate.of(2026, 5, 5));
    }

    @Test
    void createNaoGravaCategoria() {
        IncomeForm form = makeForm("Bônus", new BigDecimal("1000.00"),
                LocalDate.of(2026, 5, 5), "fim de ano");
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income created = service.create(alice, form);

        assertThat(created.getCategory()).isNull();
        assertThat(created.getNote()).isEqualTo("fim de ano");
    }

    @Test
    void createComFonteCadastradaUsaNomeDaEmpresaEVinculaSourceId() {
        UUID sourceId = UUID.randomUUID();
        IncomeSource source = newSource(sourceId, alice, "Acme Ltda");

        IncomeForm form = makeForm("ignorado", new BigDecimal("5000.00"),
                LocalDate.of(2026, 5, 5), null);
        form.setSourceId(sourceId);
        when(sourcesRepo.findByIdAndUserId(sourceId, alice)).thenReturn(Optional.of(source));
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income created = service.create(alice, form);

        assertThat(created.getSourceId()).isEqualTo(sourceId);
        assertThat(created.getSource()).isEqualTo("Acme Ltda");
    }

    @Test
    void createComFonteDeOutroUsuarioLancaNotFound() {
        UUID sourceId = UUID.randomUUID();
        IncomeForm form = makeForm(null, new BigDecimal("5000.00"), LocalDate.now(), null);
        form.setSourceId(sourceId);
        when(sourcesRepo.findByIdAndUserId(sourceId, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(bob, form))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("incomeSource.notFound");
        verify(incomesRepo, never()).save(any(Income.class));
    }

    @Test
    void createSemFonteNemOrigemRecusa() {
        IncomeForm form = makeForm("   ", new BigDecimal("100.00"), LocalDate.now(), null);

        assertThatThrownBy(() -> service.create(alice, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("income.sourceRequired");
        verify(incomesRepo, never()).save(any(Income.class));
    }

    @Test
    void lancamentoAvulsoNasceConfirmadoComoRecebido() {
        IncomeForm form = makeForm("Freela", new BigDecimal("800.00"), LocalDate.now(), null);
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        // Lançar é registrar o que já caiu — quem programa é a receita fixa.
        Income created = service.create(alice, form);

        assertThat(created.isReceived()).isTrue();
        assertThat(created.getReceivedAt()).isEqualTo(NOW);
    }

    @Test
    void toggleReceivedConfirmaEDesfaz() {
        UUID id = UUID.randomUUID();
        Income pendente = new Income();
        pendente.setId(id);
        pendente.setUserId(alice);
        pendente.setReceived(false);
        when(incomesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(pendente));
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income confirmado = service.toggleReceived(alice, id);
        assertThat(confirmado.isReceived()).isTrue();
        assertThat(confirmado.getReceivedAt()).isEqualTo(NOW);

        Income desfeito = service.toggleReceived(alice, id);
        assertThat(desfeito.isReceived()).isFalse();
        assertThat(desfeito.getReceivedAt()).isNull();
    }

    @Test
    void toggleReceivedDeOutroUsuarioLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(incomesRepo.findByIdAndUserId(id, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleReceived(bob, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(incomesRepo, never()).save(any(Income.class));
    }

    @Test
    void requireDeOutroUsuarioLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(incomesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(alice, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAlteraCamposEPreservaUserIdECategoriaAntiga() {
        UUID id = UUID.randomUUID();
        Income existing = new Income();
        existing.setId(id);
        existing.setUserId(alice);
        existing.setSource("Antigo");
        existing.setAmountCents(50_000L);
        existing.setIncomeDate(LocalDate.of(2026, 4, 1));
        existing.setCategory("outros");

        IncomeForm form = makeForm("Novo", new BigDecimal("750.00"),
                LocalDate.of(2026, 5, 10), "obs");

        when(incomesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(existing));
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income updated = service.update(alice, id, form);

        assertThat(updated.getUserId()).isEqualTo(alice);
        assertThat(updated.getSource()).isEqualTo("Novo");
        assertThat(updated.getAmountCents()).isEqualTo(75_000L);
        assertThat(updated.getIncomeDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(updated.getNote()).isEqualTo("obs");
        // A UI não edita mais categoria — editar o valor não pode apagar
        // o dado de um registro antigo.
        assertThat(updated.getCategory()).isEqualTo("outros");
    }

    @Test
    void deleteRemoveSomenteAposVerificarOwnership() {
        UUID id = UUID.randomUUID();
        Income i = new Income();
        i.setId(id);
        i.setUserId(alice);
        when(incomesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(i));

        service.delete(alice, id);
        verify(incomesRepo).delete(i);
    }

    @Test
    void deleteDeOutroUsuarioLancaNotFoundENaoChamaDelete() {
        UUID id = UUID.randomUUID();
        when(incomesRepo.findByIdAndUserId(id, bob)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(bob, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(incomesRepo, never()).delete(any(Income.class));
    }

    @Test
    void noteEmBrancoVeraNaPersistenciaComoNull() {
        IncomeForm form = makeForm("Salário", new BigDecimal("100.00"),
                LocalDate.now(), "   ");
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income created = service.create(alice, form);

        assertThat(created.getNote()).isNull();
    }

    // ── helpers ──────────────────────────────────────────────

    private IncomeForm makeForm(String source, BigDecimal amount, LocalDate date, String note) {
        IncomeForm f = new IncomeForm();
        f.setSource(source);
        f.setAmount(amount);
        f.setIncomeDate(date);
        f.setNote(note);
        return f;
    }

    private static IncomeSource newSource(UUID id, UUID userId, String name) {
        IncomeSource s = new IncomeSource();
        s.setId(id);
        s.setUserId(userId);
        s.setName(name);
        s.setAmountCents(500_000L);
        s.setPayBusinessDay((short) 5);
        return s;
    }
}

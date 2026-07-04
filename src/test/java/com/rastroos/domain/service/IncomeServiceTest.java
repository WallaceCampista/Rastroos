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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.Income;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.web.form.IncomeForm;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

    @Mock private IncomeRepository incomesRepo;
    @Mock private CategoryRepository categoriesRepo;

    @InjectMocks private IncomeService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob   = UUID.randomUUID();

    @Test
    void createConverteAmountParaCentavosCorretamente() {
        IncomeForm form = makeForm("Salário", new BigDecimal("3500.00"),
                LocalDate.of(2026, 5, 5), "outros", null);
        when(categoriesRepo.existsById("outros")).thenReturn(true);
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income created = service.create(alice, form);

        assertThat(created.getUserId()).isEqualTo(alice);
        assertThat(created.getSource()).isEqualTo("Salário");
        assertThat(created.getAmountCents()).isEqualTo(350_000L);
        assertThat(created.getIncomeDate()).isEqualTo(LocalDate.of(2026, 5, 5));
        assertThat(created.getCategory()).isEqualTo("outros");
    }

    @Test
    void createSemCategoriaAceitaCategoriaNula() {
        IncomeForm form = makeForm("Bônus", new BigDecimal("1000.00"),
                LocalDate.of(2026, 5, 5), null, "fim de ano");
        // categoria nula → service nem chama existsById
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income created = service.create(alice, form);

        assertThat(created.getCategory()).isNull();
        assertThat(created.getNote()).isEqualTo("fim de ano");
        verify(categoriesRepo, never()).existsById(any());
    }

    @Test
    void createComCategoriaInexistenteLancaNotFound() {
        IncomeForm form = makeForm("Salário", new BigDecimal("100.00"),
                LocalDate.now(), "inexistente", null);
        when(categoriesRepo.existsById("inexistente")).thenReturn(false);

        assertThatThrownBy(() -> service.create(alice, form))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("category.notFound");
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
    void updateAlteraCamposEPreservaUserIdECreatedAt() {
        UUID id = UUID.randomUUID();
        Income existing = new Income();
        existing.setId(id);
        existing.setUserId(alice);
        existing.setSource("Antigo");
        existing.setAmountCents(50_000L);
        existing.setIncomeDate(LocalDate.of(2026, 4, 1));
        existing.setCategory("outros");

        IncomeForm form = makeForm("Novo", new BigDecimal("750.00"),
                LocalDate.of(2026, 5, 10), "outros", "obs");

        when(incomesRepo.findByIdAndUserId(id, alice)).thenReturn(Optional.of(existing));
        when(categoriesRepo.existsById("outros")).thenReturn(true);
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income updated = service.update(alice, id, form);

        assertThat(updated.getUserId()).isEqualTo(alice);
        assertThat(updated.getSource()).isEqualTo("Novo");
        assertThat(updated.getAmountCents()).isEqualTo(75_000L);
        assertThat(updated.getIncomeDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(updated.getNote()).isEqualTo("obs");
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
                LocalDate.now(), null, "   ");
        when(incomesRepo.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

        Income created = service.create(alice, form);

        assertThat(created.getNote()).isNull();
    }

    // ── helpers ──────────────────────────────────────────────

    private IncomeForm makeForm(String source, BigDecimal amount, LocalDate date,
                                String categoryId, String note) {
        IncomeForm f = new IncomeForm();
        f.setSource(source);
        f.setAmount(amount);
        f.setIncomeDate(date);
        f.setCategoryId(categoryId);
        f.setNote(note);
        return f;
    }
}

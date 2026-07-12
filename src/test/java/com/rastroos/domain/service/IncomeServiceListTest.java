package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Income;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.web.dto.IncomeFilter;
import com.rastroos.web.dto.IncomesPageView;

/**
 * Cobre {@code listForMonth}/{@code toDto} do {@link IncomeService}:
 * normalização de filtro, i18n (pt/en) e receita sem categoria.
 */
@ExtendWith(MockitoExtension.class)
class IncomeServiceListTest {

    @Mock private IncomeRepository incomes;
    @Mock private CategoryRepository categories;

    private IncomeService service;

    private final UUID userId = UUID.randomUUID();

    private IncomeService service() {
        if (service == null) {
            service = new IncomeService(incomes, categories);
        }
        return service;
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void listComFiltroEmInglesMapeiaCategoriaEReceitaSemCategoria() {
        LocaleContextHolder.setLocale(java.util.Locale.ENGLISH);

        Income withCat = income("Salário", 350000, "salary");
        Income noCat = income("Presente", 5000, null);
        when(incomes.searchByFilters(eq(userId), any(), any(), eq("salary"), eq("sal"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withCat, noCat), PageRequest.of(0, 20), 2));
        when(categories.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(category("salary", "Salário", "Salary")));
        when(incomes.totalByFilters(eq(userId), any(), any(), eq("salary"), eq("sal")))
                .thenReturn(355000L);

        IncomeFilter filter = new IncomeFilter("salary", "  sal ");
        IncomesPageView view = service().listForMonth(userId, YearMonth.of(2026, 5), filter, 0, 20);

        assertThat(view.items()).hasSize(2);
        assertThat(view.items().get(0).categoryName()).isEqualTo("Salary");   // inglês
        assertThat(view.items().get(0).categoryColorHex()).isEqualTo("#00aa00");
        assertThat(view.items().get(1).categoryName()).isNull();               // sem categoria
        assertThat(view.totalAmount()).isEqualByComparingTo("3550.00");
    }

    @Test
    void listSemFiltroEmPortuguesClampaPaginacao() {
        LocaleContextHolder.setLocale(new java.util.Locale("pt", "BR"));

        Income i = income("Freela", 120000, "salary");
        when(incomes.searchByFilters(eq(userId), any(), any(), eq(null), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(i), PageRequest.of(0, 20), 1));
        when(categories.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(category("salary", "Salário", "Salary")));
        lenient().when(incomes.totalByFilters(any(), any(), any(), any(), any())).thenReturn(120000L);

        IncomesPageView view = service().listForMonth(userId, YearMonth.of(2026, 5), null, -1, 500);

        assertThat(view.page()).isZero();
        assertThat(view.size()).isEqualTo(100);
        assertThat(view.items().get(0).categoryName()).isEqualTo("Salário");   // português
    }

    // ── fixtures ─────────────────────────────────────────────

    private static Income income(String source, long cents, String category) {
        Income i = new Income();
        i.setId(UUID.randomUUID());
        i.setUserId(UUID.randomUUID());
        i.setSource(source);
        i.setAmountCents(cents);
        i.setIncomeDate(LocalDate.of(2026, 5, 5));
        i.setCategory(category);
        return i;
    }

    private static Category category(String id, String pt, String en) {
        Category c = new Category();
        c.setId(id);
        c.setNamePt(pt);
        c.setNameEn(en);
        c.setColorHex("#00aa00");
        return c;
    }
}

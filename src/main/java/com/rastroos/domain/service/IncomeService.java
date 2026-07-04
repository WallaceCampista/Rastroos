package com.rastroos.domain.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Income;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.web.dto.IncomeDto;
import com.rastroos.web.dto.IncomeFilter;
import com.rastroos.web.dto.IncomesPageView;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.form.IncomeForm;

/**
 * Regras de negócio para receitas.
 *
 * <p>Isolamento estrito por {@code userId}: nenhuma operação atravessa
 * usuários. Acesso a id de outro usuário → {@link ResourceNotFoundException}
 * (HTTP 404).
 */
@Service
public class IncomeService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final IncomeRepository incomes;
    private final CategoryRepository categories;

    public IncomeService(IncomeRepository incomes, CategoryRepository categories) {
        this.incomes = incomes;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public IncomesPageView listForMonth(UUID userId, YearMonth ym,
                                         IncomeFilter filter,
                                         int page, int size) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.plusMonths(1).atDay(1);

        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "incomeDate").and(Sort.by("createdAt")));

        IncomeFilter f = filter == null ? IncomeFilter.empty() : filter;
        String categoryId = blankToNull(f.categoryId());
        String search = (f.search() == null) ? "" : f.search().trim();

        Page<Income> result = incomes.searchByFilters(
                userId, start, end, categoryId, search, pageable);

        Map<String, Category> categoryById = mapCategories();
        Locale locale = LocaleContextHolder.getLocale();
        boolean english = "en".equalsIgnoreCase(locale.getLanguage());

        List<IncomeDto> items = result.getContent().stream()
                .map(i -> toDto(i, categoryById, english))
                .toList();

        long totalCents = incomes.totalByFilters(
                userId, start, end, categoryId, search);

        return new IncomesPageView(
                items,
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                MoneyDto.fromCents(totalCents)
        );
    }

    @Transactional(readOnly = true)
    public IncomeDto get(UUID userId, UUID id) {
        Income i = require(userId, id);
        boolean english = "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
        return toDto(i, mapCategories(), english);
    }

    @Transactional
    public Income create(UUID userId, IncomeForm form) {
        validateCategory(form.getCategoryId());

        long amountCents = form.getAmount().movePointRight(2).longValueExact();
        if (amountCents <= 0) {
            throw new IllegalArgumentException("income.amountPositive");
        }

        Income i = new Income();
        i.setUserId(userId);
        i.setSource(form.getSource().trim());
        i.setAmountCents(amountCents);
        i.setIncomeDate(form.getIncomeDate());
        i.setCategory(blankToNull(form.getCategoryId()));
        i.setNote(blankToNull(form.getNote()));
        return incomes.save(i);
    }

    @Transactional
    public Income update(UUID userId, UUID id, IncomeForm form) {
        Income existing = require(userId, id);
        validateCategory(form.getCategoryId());

        long amountCents = form.getAmount().movePointRight(2).longValueExact();
        if (amountCents <= 0) {
            throw new IllegalArgumentException("income.amountPositive");
        }
        existing.setSource(form.getSource().trim());
        existing.setAmountCents(amountCents);
        existing.setIncomeDate(form.getIncomeDate());
        existing.setCategory(blankToNull(form.getCategoryId()));
        existing.setNote(blankToNull(form.getNote()));
        return incomes.save(existing);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Income i = require(userId, id);
        incomes.delete(i);
    }

    @Transactional(readOnly = true)
    public Income require(UUID userId, UUID id) {
        return incomes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("income.notFound"));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void validateCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return;
        if (!categories.existsById(categoryId)) {
            throw new ResourceNotFoundException("category.notFound");
        }
    }

    private Map<String, Category> mapCategories() {
        Map<String, Category> map = new HashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            map.put(c.getId(), c);
        }
        return map;
    }

    private static IncomeDto toDto(Income i,
                                   Map<String, Category> categoryById,
                                   boolean english) {
        Category category = i.getCategory() == null ? null : categoryById.get(i.getCategory());
        return new IncomeDto(
                i.getId(),
                i.getSource(),
                MoneyDto.fromCents(i.getAmountCents()),
                i.getIncomeDate(),
                i.getCategory(),
                category == null ? null
                        : (english ? category.getNameEn() : category.getNamePt()),
                category == null ? null : category.getColorHex(),
                i.getNote()
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

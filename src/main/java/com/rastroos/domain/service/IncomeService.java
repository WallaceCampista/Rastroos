package com.rastroos.domain.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.IncomeSource;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.IncomeSourceRepository;
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
 *
 * <p>Receita não tem categoria: para lançar bastam o valor e a origem — que
 * pode vir de uma fonte recorrente já cadastrada ({@code sourceId}) ou ser
 * digitada. A coluna {@code category} continua no banco por causa dos
 * registros antigos, mas nada novo a preenche e uma edição não a apaga.
 *
 * <p>Lançar aqui é registrar algo que <b>já aconteceu</b>, então o lançamento
 * avulso nasce confirmado. Os recebimentos programados por uma receita fixa
 * nascem pendentes e só entram nos totais depois do
 * {@link #toggleReceived(UUID, UUID)}.
 */
@Service
public class IncomeService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final IncomeRepository incomes;
    private final IncomeSourceRepository sources;
    private final CategoryRepository categories;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public IncomeService(IncomeRepository incomes,
                         IncomeSourceRepository sources,
                         CategoryRepository categories,
                         Clock clock,
                         ApplicationEventPublisher events) {
        this.incomes = incomes;
        this.sources = sources;
        this.categories = categories;
        this.clock = clock;
        this.events = events;
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
        long receivedCents = incomes.receivedTotalByFilters(
                userId, start, end, categoryId, search);

        return new IncomesPageView(
                items,
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                MoneyDto.fromCents(totalCents),
                MoneyDto.fromCents(receivedCents)
        );
    }

    @Transactional(readOnly = true)
    public IncomeDto get(UUID userId, UUID id) {
        Income i = require(userId, id);
        boolean english = "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
        return toDto(i, mapCategories(), english);
    }

    @Transactional
    /**
     * Resultado de {@link #create}: além da linha, diz se ela já existia.
     *
     * @param confirmed {@code true} quando a ocorrência programada da receita
     *                  fixa foi confirmada, em vez de uma nova linha criada
     */
    public record CreateResult(Income income, boolean confirmed) {
    }

    public Income create(UUID userId, IncomeForm form) {
        return createOrConfirm(userId, form).income();
    }

    /**
     * Lança uma receita avulsa — ou, quando o usuário escolhe uma receita fixa
     * cadastrada, <b>confirma a ocorrência daquele mês</b> em vez de criar
     * outra.
     *
     * <p>Criar uma linha nova ali duplicava o valor do mês: a receita fixa já
     * materializa 10 anos de ocorrências ao ser cadastrada (ver
     * {@code IncomeSourceService}), então a do mês escolhido já existe,
     * apenas ainda não recebida. O que falta é confirmar — não recadastrar.
     * O valor e a data digitados vencem os programados (o depósito pode ter
     * vindo diferente do combinado).
     */
    @Transactional
    public CreateResult createOrConfirm(UUID userId, IncomeForm form) {
        IncomeSource source = resolveSource(userId, form);

        if (source != null) {
            YearMonth mes = YearMonth.from(form.getIncomeDate());
            Optional<Income> programada = incomes
                    .findFirstByUserIdAndSourceIdAndIncomeDateBetweenOrderByIncomeDateAsc(
                            userId, source.getId(), mes.atDay(1), mes.atEndOfMonth());
            if (programada.isPresent()) {
                Income i = programada.get();
                i.setAmountCents(amountCentsOf(form));
                i.setIncomeDate(form.getIncomeDate());
                if (blankToNull(form.getNote()) != null) {
                    i.setNote(blankToNull(form.getNote()));
                }
                i.setReceived(true);
                i.setReceivedAt(clock.instant());
                Income saved = incomes.save(i);
                dataChanged(userId);
                return new CreateResult(saved, true);
            }
            // Sem ocorrência no mês (fonte criada depois, ou a linha foi
            // apagada): cria uma, ainda vinculada à fonte.
        }

        Income i = new Income();
        i.setUserId(userId);
        i.setSourceId(source == null ? null : source.getId());
        i.setSource(sourceLabel(source, form));
        i.setAmountCents(amountCentsOf(form));
        i.setIncomeDate(form.getIncomeDate());
        i.setNote(blankToNull(form.getNote()));
        // Lançar é registrar o que já caiu; quem programa é a receita fixa.
        i.setReceived(true);
        i.setReceivedAt(clock.instant());
        Income saved = incomes.save(i);
        dataChanged(userId);
        return new CreateResult(saved, false);
    }

    @Transactional
    public Income update(UUID userId, UUID id, IncomeForm form) {
        Income existing = require(userId, id);
        IncomeSource source = resolveSource(userId, form);

        existing.setSourceId(source == null ? null : source.getId());
        existing.setSource(sourceLabel(source, form));
        existing.setAmountCents(amountCentsOf(form));
        existing.setIncomeDate(form.getIncomeDate());
        existing.setNote(blankToNull(form.getNote()));
        // `category` fica como está: a UI não edita mais categoria, e sobrescrever
        // aqui apagaria o dado de um registro antigo em toda edição de valor.
        Income saved = incomes.save(existing);
        dataChanged(userId);
        return saved;
    }

    /**
     * Confirma (ou desfaz) o recebimento. Enquanto não confirmado, o valor não
     * entra em nenhum total de "recebido" — programado não é recebido.
     */
    @Transactional
    public Income toggleReceived(UUID userId, UUID id) {
        Income i = require(userId, id);
        if (i.isReceived()) {
            i.setReceived(false);
            i.setReceivedAt(null);
        } else {
            i.setReceived(true);
            i.setReceivedAt(clock.instant());
        }
        Income saved = incomes.save(i);
        dataChanged(userId);
        return saved;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Income i = require(userId, id);
        incomes.delete(i);
        dataChanged(userId);
    }

    @Transactional(readOnly = true)
    public Income require(UUID userId, UUID id) {
        return incomes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("income.notFound"));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * A fonte escolhida no formulário, validada como do próprio usuário.
     * {@code null} quando o lançamento é avulso (origem digitada).
     */
    private IncomeSource resolveSource(UUID userId, IncomeForm form) {
        if (form.getSourceId() == null) {
            if (blankToNull(form.getSource()) == null) {
                throw new IllegalArgumentException("income.sourceRequired");
            }
            return null;
        }
        return sources.findByIdAndUserId(form.getSourceId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("incomeSource.notFound"));
    }

    /** Com fonte cadastrada, o nome da empresa vence o que veio digitado. */
    private static String sourceLabel(IncomeSource source, IncomeForm form) {
        return source != null ? source.getName() : form.getSource().trim();
    }

    private static long amountCentsOf(IncomeForm form) {
        long cents = form.getAmount().movePointRight(2).longValueExact();
        if (cents <= 0) {
            throw new IllegalArgumentException("income.amountPositive");
        }
        return cents;
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
                i.getSourceId(),
                MoneyDto.fromCents(i.getAmountCents()),
                i.getIncomeDate(),
                i.getCategory(),
                category == null ? null
                        : (english ? category.getNameEn() : category.getNamePt()),
                category == null ? null : category.getColorHex(),
                i.getNote(),
                i.isReceived()
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Avisa que os dados financeiros do usuário mudaram. O evento só é
     * entregue depois do commit ({@code AFTER_COMMIT}), então um rollback não
     * marca nada — e é essa marca que faz o Alfredo regerar os resumos das
     * telas. Sem escrita, nenhum resumo é regerado e nada é consumido.
     */
    private void dataChanged(UUID userId) {
        events.publishEvent(new UserDataChangedEvent(userId));
    }

}

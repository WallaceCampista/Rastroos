package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.TransactionFilter;
import com.rastroos.web.dto.TransactionFilter.FixedFilter;
import com.rastroos.web.dto.TransactionFilter.PaidFilter;
import com.rastroos.web.dto.TransactionFilterCounts;
import com.rastroos.web.dto.TransactionsPageView;

/**
 * Cobre {@code listForMonth}/{@code toDto} do {@link TransactionService} —
 * caminho de filtros (switches paid/fixed), i18n (pt/en) e fallback de
 * conta/categoria ausentes.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceListTest {

    @Mock private TransactionRepository transactions;
    @Mock private AccountRepository accounts;
    @Mock private CategoryRepository categories;

    private TransactionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private TransactionService service() {
        if (service == null) {
            service = new TransactionService(transactions, accounts, categories);
        }
        return service;
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void listComFiltrosPagoFixoEmInglesMapeiaContaECategoria() {
        LocaleContextHolder.setLocale(java.util.Locale.ENGLISH);

        Transaction full = tx("Mercado", accountId, "food", true, true);
        Transaction orphan = tx("Sem refs", UUID.randomUUID(), "ghost", false, false);
        Page<Transaction> page = new PageImpl<>(List.of(full, orphan), PageRequest.of(0, 20), 2);
        when(transactions.searchByFilters(eq(userId), any(), any(),
                eq(Boolean.TRUE), eq(Boolean.TRUE), any(), eq("food"), eq("mercado"), any(Pageable.class)))
                .thenReturn(page);
        when(accounts.findAllByUserIdOrderByNameAsc(userId)).thenReturn(List.of(account(accountId, "Nubank")));
        when(categories.findAllByOrderBySortOrderAsc()).thenReturn(List.of(category("food", "Alimentação", "Food")));
        when(transactions.totalsByFilters(eq(userId), any(), any(),
                eq(Boolean.TRUE), eq(Boolean.TRUE), any(), eq("food"), eq("mercado")))
                .thenReturn(List.<Object[]>of(new Object[] { 1000L, 400L }));

        TransactionFilter filter = new TransactionFilter(
                PaidFilter.PAID, null, "food", FixedFilter.FIXED, "  mercado  ");
        TransactionsPageView view = service().listForMonth(userId, YearMonth.of(2026, 5), filter, 0, 20);

        assertThat(view.items()).hasSize(2);
        assertThat(view.items().get(0).accountName()).isEqualTo("Nubank");
        assertThat(view.items().get(0).categoryName()).isEqualTo("Food");        // inglês
        // conta/categoria inexistentes → fallback
        assertThat(view.items().get(1).accountName()).isEmpty();
        assertThat(view.items().get(1).categoryName()).isEqualTo("ghost");        // usa o id
        assertThat(view.totalAmount()).isEqualByComparingTo("10.00");
        assertThat(view.totalPaid()).isEqualByComparingTo("4.00");
    }

    @Test
    void listSemFiltroEmPortuguesUsaAllETotaisZeradosQuandoVazio() {
        LocaleContextHolder.setLocale(new java.util.Locale("pt", "BR"));

        Transaction full = tx("Aluguel", accountId, "home", false, true);
        when(transactions.searchByFilters(eq(userId), any(), any(),
                eq(null), eq(null), any(), eq(null), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(full), PageRequest.of(0, 20), 1));
        when(accounts.findAllByUserIdOrderByNameAsc(userId)).thenReturn(List.of(account(accountId, "Itaú")));
        when(categories.findAllByOrderBySortOrderAsc()).thenReturn(List.of(category("home", "Casa", "Home")));
        // sem linha de totais → cai no fallback {0,0}
        lenient().when(transactions.totalsByFilters(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        TransactionsPageView view = service().listForMonth(userId, YearMonth.of(2026, 5), null, -3, 999);

        assertThat(view.page()).isZero();          // page clamped
        assertThat(view.size()).isEqualTo(100);    // size clamped ao máximo
        assertThat(view.items().get(0).categoryName()).isEqualTo("Casa");  // português
        assertThat(view.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(view.totalPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    void listComUnpaidEOneOffPercorreOsDemaisRamosDoSwitch() {
        when(transactions.searchByFilters(eq(userId), any(), any(),
                eq(Boolean.FALSE), eq(Boolean.FALSE), any(), eq(null), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        lenient().when(accounts.findAllByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        lenient().when(categories.findAllByOrderBySortOrderAsc()).thenReturn(List.of());
        when(transactions.totalsByFilters(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { 0L, 0L }));

        TransactionFilter filter = new TransactionFilter(
                PaidFilter.UNPAID, null, "  ", FixedFilter.ONE_OFF, null);
        TransactionsPageView view = service().listForMonth(userId, YearMonth.of(2026, 5), filter, 0, 20);

        assertThat(view.items()).isEmpty();
        assertThat(view.totalElements()).isZero();
    }

    @Test
    void countsForMonthMapeiaBucketsDaLinhaDoRepositorio() {
        when(transactions.countsByFilters(eq(userId), any(), any(), any(), eq("food"), eq("mercado")))
                .thenReturn(List.<Object[]>of(new Object[] { 20L, 5L, 15L, 8L, 12L }));

        TransactionFilter filter = new TransactionFilter(
                PaidFilter.PAID, null, "food", FixedFilter.FIXED, "  mercado  ");
        TransactionFilterCounts counts = service().countsForMonth(userId, YearMonth.of(2026, 8), filter);

        assertThat(counts.total()).isEqualTo(20);
        assertThat(counts.paid()).isEqualTo(5);
        assertThat(counts.unpaid()).isEqualTo(15);
        assertThat(counts.fixed()).isEqualTo(8);
        assertThat(counts.oneOff()).isEqualTo(12);
        // buckets se fecham: pago + aberto = total; fixo + pontual = total
        assertThat(counts.paid() + counts.unpaid()).isEqualTo(counts.total());
        assertThat(counts.fixed() + counts.oneOff()).isEqualTo(counts.total());
    }

    @Test
    void countsForMonthSemLinhaRetornaZeros() {
        when(transactions.countsByFilters(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        TransactionFilterCounts counts = service().countsForMonth(userId, YearMonth.of(2026, 8), null);

        assertThat(counts.total()).isZero();
        assertThat(counts.paid()).isZero();
        assertThat(counts.unpaid()).isZero();
        assertThat(counts.fixed()).isZero();
        assertThat(counts.oneOff()).isZero();
    }

    // ── fixtures ─────────────────────────────────────────────

    private static Transaction tx(String desc, UUID accountId, String categoryId,
                                  boolean fixed, boolean paid) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setDescription(desc);
        t.setAccountId(accountId);
        t.setCategoryId(categoryId);
        t.setAmountCents(500);
        t.setDueDate(java.time.LocalDate.of(2026, 5, 10));
        t.setFixed(fixed);
        t.setPaid(paid);
        return t;
    }

    private static Account account(UUID id, String name) {
        Account a = new Account();
        a.setId(id);
        a.setName(name);
        a.setColorHex("#123456");
        return a;
    }

    private static Category category(String id, String pt, String en) {
        Category c = new Category();
        c.setId(id);
        c.setNamePt(pt);
        c.setNameEn(en);
        c.setColorHex("#abcdef");
        return c;
    }
}

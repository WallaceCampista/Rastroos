package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.rastroos.config.ExtractionProperties;
import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.BusinessRuleException;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.web.dto.AccountSummaryDto;
import com.rastroos.web.dto.CategoryOptionDto;
import com.rastroos.web.dto.InvoiceImportResult;
import com.rastroos.web.dto.InvoiceReviewItem;
import com.rastroos.web.dto.InvoiceReviewView;
import com.rastroos.web.dto.MoneyDto;
import com.rastroos.web.form.InvoiceImportForm;
import com.rastroos.web.form.InvoiceItemForm;

/**
 * Importação de fatura de cartão de crédito: ler → conferir → lançar.
 *
 * <p>Nada é gravado sem a conferência. A leitura ({@link #read}) e o recálculo
 * ({@link #review}) só montam a tela; {@link #commit} grava os itens marcados.
 * Na gravação o cruzamento com o que já está lançado é <strong>refeito</strong>
 * com o banco daquele instante e com a conta travada: um item que já existe
 * não é criado de novo, mesmo que venha marcado (duplo clique, duas abas,
 * formulário adulterado). É isso que torna a importação idempotente.
 *
 * <p>Isolamento: toda leitura e escrita filtra pelo usuário; conta de outro
 * usuário responde 404.
 */
@Service
public class InvoiceImportService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final InvoiceVisionReader reader;
    private final ExtractionProperties uploadProps;
    private final ApplicationEventPublisher events;

    public InvoiceImportService(AccountRepository accounts, TransactionRepository transactions,
                                CategoryRepository categories, InvoiceVisionReader reader,
                                ExtractionProperties uploadProps, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.categories = categories;
        this.reader = reader;
        this.uploadProps = uploadProps;
        this.events = events;
    }

    /**
     * Lê a fatura com a IA e monta a conferência. Sem {@code @Transactional} de
     * propósito: a espera pelo provedor não pode segurar conexão do pool (§4.1).
     *
     * @param period mês aberto no detalhe — só usado se a fatura não trouxer vencimento legível
     * @throws com.rastroos.domain.exception.InvalidUploadException arquivo inválido
     * @throws com.rastroos.domain.exception.InvoiceReadException  leitura impossível
     */
    public InvoiceReviewView read(UUID userId, UUID accountId, MultipartFile file, YearMonth period) {
        Account account = requireCreditCard(userId, accountId);
        UploadGuard.validate(file, uploadProps.getMaxFileSizeBytes(),
                UploadGuard.DOCUMENT_TYPES, UploadGuard.DOCUMENT_EXTENSIONS);

        List<Category> cats = categories.findAllByOrderBySortOrderAsc();
        InvoiceReading reading = reader.read(userId, file, categoryNames(cats));

        InvoiceImportForm form = new InvoiceImportForm();
        form.setDueDateRead(reading.dueDate() != null);
        form.setDueDate(reading.dueDate() != null ? reading.dueDate() : fallbackDueDate(account, period));
        form.setTotalRead(reading.total());
        form.setLast4Read(reading.last4());
        for (InvoiceLine line : reading.lines()) {
            InvoiceItemForm item = new InvoiceItemForm();
            item.setDateLabel(line.dateLabel());
            item.setDescription(line.description());
            item.setAmount(line.amount());
            item.setInstallment(line.installment());
            item.setInstallments(line.installments());
            item.setType(line.type());
            item.setCategoryId(line.categoryId());
            form.getItems().add(item);
        }
        return buildReview(userId, account, form, cats);
    }

    /** Refaz a conferência (ex.: a pessoa corrigiu o vencimento). Não grava nada. */
    @Transactional(readOnly = true)
    public InvoiceReviewView review(UUID userId, UUID accountId, InvoiceImportForm form) {
        Account account = requireCreditCard(userId, accountId);
        return buildReview(userId, account, form, categories.findAllByOrderBySortOrderAsc());
    }

    /** Conferência vazia com a mensagem de erro, para o modal explicar o que houve. */
    @Transactional(readOnly = true)
    public InvoiceReviewView failure(UUID userId, UUID accountId, String errorKey) {
        return InvoiceReviewView.failure(header(requireCreditCard(userId, accountId)), errorKey);
    }

    /**
     * Lança os itens marcados. Linha que já existe no cartão é pulada; parcela
     * nova cria também as parcelas seguintes que ainda não existirem; parcela
     * já lançada com centavos de diferença recebe o valor da fatura, se marcada.
     */
    @Transactional
    public InvoiceImportResult commit(UUID userId, UUID accountId, InvoiceImportForm form) {
        Account account = accounts.lockByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("account.notFound"));
        ensureCreditCard(account);

        Set<String> validCategories = categories.findAllByOrderBySortOrderAsc().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        List<Integer> importable = importableIndexes(form);
        InvoiceReconciler r = reconcile(userId, account, form, importable);
        LocalDate dueDate = form.getDueDate();

        List<Transaction> toSave = new ArrayList<>();
        int created = 0;
        int futureCreated = 0;
        int adjusted = 0;

        for (int pos = 0; pos < importable.size(); pos++) {
            InvoiceItemForm item = form.getItems().get(importable.get(pos));
            if (!item.isSelected()) {
                continue;
            }
            InvoiceReconciler.Status status = r.status(pos);
            if (status == InvoiceReconciler.Status.EXISTING) {
                continue;
            }
            if (status == InvoiceReconciler.Status.ADJUST) {
                Transaction existing = r.match(pos);
                existing.setAmountCents(cents(item.getAmount()));
                toSave.add(existing);
                adjusted++;
                continue;
            }
            if (!validCategories.contains(item.getCategoryId())) {
                throw new ResourceNotFoundException("category.notFound");
            }

            List<InvoiceReconciler.Future> futures = r.futures(pos);
            boolean installment = item.getInstallment() != null && item.getInstallments() != null
                    && item.getInstallments() > 1;
            UUID seriesId = installment ? InvoiceReconciler.seriesFor(futures) : null;

            toSave.add(newTransaction(userId, account.getId(), item, dueDate,
                    installment ? item.getInstallment() : null, seriesId));
            created++;
            for (InvoiceReconciler.Future future : futures) {
                if (future.toCreate()) {
                    toSave.add(newTransaction(userId, account.getId(), item, future.dueDate(),
                            future.number(), seriesId));
                    futureCreated++;
                }
            }
        }

        if (!toSave.isEmpty()) {
            transactions.saveAll(toSave);
            events.publishEvent(new UserDataChangedEvent(userId));
        }
        return new InvoiceImportResult(created, futureCreated, adjusted, YearMonth.from(dueDate));
    }

    // ── Conferência ──────────────────────────────────────────────────────

    private InvoiceReviewView buildReview(UUID userId, Account account, InvoiceImportForm form,
                                          List<Category> cats) {
        List<Integer> importable = importableIndexes(form);
        InvoiceReconciler r = reconcile(userId, account, form, importable);
        boolean english = isEnglish();

        List<InvoiceReviewItem> items = new ArrayList<>();
        for (int pos = 0; pos < importable.size(); pos++) {
            int index = importable.get(pos);
            InvoiceItemForm item = form.getItems().get(index);
            InvoiceReconciler.Status status = r.status(pos);
            Transaction match = r.match(pos);

            // Parcelas seguintes só contam para o que será criado; na ordem das
            // linhas, como na gravação.
            int futureToCreate = 0;
            int futureExisting = 0;
            if (status == InvoiceReconciler.Status.NEW || status == InvoiceReconciler.Status.POSSIBLE_DUPLICATE) {
                for (InvoiceReconciler.Future f : r.futures(pos)) {
                    if (f.toCreate()) futureToCreate++;
                    else futureExisting++;
                }
            }

            items.add(new InvoiceReviewItem(
                    index,
                    status == InvoiceReconciler.Status.NEW || status == InvoiceReconciler.Status.ADJUST,
                    item.getDateLabel(),
                    item.getDescription(),
                    item.getAmount(),
                    item.getInstallment(),
                    item.getInstallments(),
                    item.getType(),
                    item.getCategoryId(),
                    statusKey(status),
                    status != InvoiceReconciler.Status.EXISTING,
                    match == null ? null : match.getDescription(),
                    match == null ? null : MoneyDto.fromCents(match.getAmountCents()),
                    futureToCreate,
                    futureExisting));
        }

        List<InvoiceReviewItem> ignored = new ArrayList<>();
        for (int index = 0; index < form.getItems().size(); index++) {
            InvoiceItemForm item = form.getItems().get(index);
            if (!item.getType().importable()) {
                ignored.add(new InvoiceReviewItem(index, false, item.getDateLabel(), item.getDescription(),
                        item.getAmount(), null, null, item.getType(), item.getCategoryId(),
                        "ignored", false, null, null, 0, 0));
            }
        }

        List<CategoryOptionDto> options = cats.stream()
                .map(c -> new CategoryOptionDto(c.getId(), english ? c.getNameEn() : c.getNamePt(), c.getColorHex()))
                .toList();

        return new InvoiceReviewView(account.getId(), account.getName(), account.getColorHex(),
                account.getIconText(), account.getLast4(), null,
                form.getDueDate(), form.isDueDateRead(), form.getTotalRead(), form.getLast4Read(),
                items, ignored, options);
    }

    private InvoiceReconciler reconcile(UUID userId, Account account, InvoiceImportForm form,
                                        List<Integer> importable) {
        LocalDate dueDate = form.getDueDate();
        List<InvoiceReconciler.Line> lines = importable.stream()
                .map(i -> form.getItems().get(i))
                .map(item -> new InvoiceReconciler.Line(
                        item.getDescription().trim(),
                        cents(item.getAmount()),
                        item.getInstallment(),
                        item.getInstallments()))
                .toList();
        List<Transaction> existing = transactions.findAllByUserIdAndAccountIdAndDueDateGreaterThanEqual(
                userId, account.getId(), YearMonth.from(dueDate).atDay(1));
        return InvoiceReconciler.reconcile(lines, dueDate, existing);
    }

    private static List<Integer> importableIndexes(InvoiceImportForm form) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < form.getItems().size(); i++) {
            if (form.getItems().get(i).getType().importable()) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static String statusKey(InvoiceReconciler.Status status) {
        return switch (status) {
            case NEW -> "new";
            case EXISTING -> "existing";
            case ADJUST -> "adjust";
            case POSSIBLE_DUPLICATE -> "duplicate";
        };
    }

    // ── Apoio ────────────────────────────────────────────────────────────

    private static Transaction newTransaction(UUID userId, UUID accountId, InvoiceItemForm item,
                                              LocalDate dueDate, Short installment, UUID seriesId) {
        Transaction t = new Transaction();
        t.setUserId(userId);
        t.setAccountId(accountId);
        t.setCategoryId(item.getCategoryId());
        t.setDescription(item.getDescription().trim());
        t.setAmountCents(cents(item.getAmount()));
        t.setDueDate(dueDate);
        t.setFixed(false);
        // Cartão se paga pela fatura inteira ("Pagar fatura"), não item a item.
        t.setPaid(false);
        if (installment != null) {
            t.setInstallmentCurrent(installment);
            t.setInstallmentTotal(item.getInstallments());
        }
        t.setSeriesId(seriesId);
        return t;
    }

    private static long cents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    /** Sem vencimento legível: o dia de vencimento do cartão no mês aberto. */
    private static LocalDate fallbackDueDate(Account account, YearMonth period) {
        int day = account.getDueDay() != null ? account.getDueDay() : 1;
        return period.atDay(Math.min(day, period.lengthOfMonth()));
    }

    private Account requireCreditCard(UUID userId, UUID accountId) {
        Account account = accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("account.notFound"));
        ensureCreditCard(account);
        return account;
    }

    /** Fatura só existe em cartão de crédito. */
    private static void ensureCreditCard(Account account) {
        if (account.getKind() != AccountKind.CARD) {
            throw new BusinessRuleException("account.invoice.notCredit");
        }
    }

    private static Map<String, String> categoryNames(List<Category> cats) {
        Map<String, String> names = new LinkedHashMap<>();
        cats.forEach(c -> names.put(c.getId(), c.getNamePt()));
        return names;
    }

    private static AccountSummaryDto header(Account a) {
        return new AccountSummaryDto(a.getId(), a.getName(), a.getKind(), a.getColorHex(), a.getIconText(),
                a.getLast4(), a.getCloseDay(), a.getDueDay(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, 0, "none");
    }

    private static boolean isEnglish() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null && "en".equalsIgnoreCase(locale.getLanguage());
    }
}

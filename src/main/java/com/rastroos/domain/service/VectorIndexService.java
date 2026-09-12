package com.rastroos.domain.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Category;
import com.rastroos.domain.entity.Income;
import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.InvestmentRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.domain.repository.VectorStoreRepository;

/**
 * Mantém o índice semântico do usuário em dia (tabela {@code ai_documents}).
 *
 * <p>Indexa <strong>texto livre</strong> — descrição de lançamento, fonte de
 * receita, nome de conta e de investimento — que é onde a busca por
 * similaridade ajuda de verdade ("aquela compra da farmácia"). Valor e data
 * viajam junto como metadado, mas nenhuma conta é feita a partir daqui: os
 * números vêm do {@link FinancialContextBuilder}.
 *
 * <p>Reindexação é <strong>incremental por impressão digital</strong>: o texto
 * de cada linha vira um SHA-256 e só o que mudou é re-embeddado. Um usuário com
 * mil lançamentos que edita um deles paga um embedding, não mil.
 */
@Service
public class VectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexService.class);

    /** Janela indexada: mais do que isso não é o que se pergunta no dia a dia. */
    static final int INDEXED_MONTHS = 24;

    static final String KIND_TRANSACTION = "TRANSACTION";
    static final String KIND_INCOME = "INCOME";
    static final String KIND_ACCOUNT = "ACCOUNT";
    static final String KIND_INVESTMENT = "INVESTMENT";

    private final TransactionRepository transactions;
    private final IncomeRepository incomes;
    private final AccountRepository accounts;
    private final InvestmentRepository investments;
    private final CategoryRepository categories;
    private final VectorStoreRepository store;
    private final EmbeddingService embeddings;
    private final AiProperties props;
    private final Clock clock;

    public VectorIndexService(TransactionRepository transactions,
                              IncomeRepository incomes,
                              AccountRepository accounts,
                              InvestmentRepository investments,
                              CategoryRepository categories,
                              VectorStoreRepository store,
                              EmbeddingService embeddings,
                              AiProperties props,
                              Clock clock) {
        this.transactions = transactions;
        this.incomes = incomes;
        this.accounts = accounts;
        this.investments = investments;
        this.categories = categories;
        this.store = store;
        this.embeddings = embeddings;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Sincroniza o índice do usuário com os dados atuais.
     *
     * @return quantos documentos foram (re)vetorizados; {@code 0} quando nada
     *         mudou — o caso comum, e o que mantém o custo perto de zero
     */
    @Transactional(readOnly = true)
    public int reindex(UUID userId) {
        if (!embeddings.isEnabled()) {
            return 0;
        }

        List<VectorDocument> desired = collect(userId);
        Map<String, String> indexed = store.hashesByUser(userId);

        List<VectorDocument> changed = new ArrayList<>();
        Set<String> stillWanted = new HashSet<>(desired.size());
        for (VectorDocument doc : desired) {
            stillWanted.add(doc.key());
            if (!doc.contentHash().equals(indexed.get(doc.key()))) {
                changed.add(doc);
            }
        }

        Set<String> orphans = new HashSet<>(indexed.keySet());
        orphans.removeAll(stillWanted);
        if (!orphans.isEmpty()) {
            store.deleteByKeys(userId, orphans);
        }

        if (changed.isEmpty()) {
            if (!orphans.isEmpty()) {
                log.debug("Índice semântico: {} documento(s) removido(s), nenhum novo", orphans.size());
            }
            return 0;
        }

        List<String> texts = changed.stream().map(VectorDocument::content).toList();
        List<float[]> vectors = embeddings.embedAll(userId, texts);
        store.upsertAll(userId, changed, vectors, embeddings.modelName());
        log.debug("Índice semântico atualizado: {} documento(s) vetorizados, {} removido(s)",
                changed.size(), orphans.size());
        return changed.size();
    }

    /** Apaga tudo o que foi indexado para o usuário (exclusão de conta/LGPD). */
    @Transactional(readOnly = true)
    public int purge(UUID userId) {
        return store.deleteAllByUser(userId);
    }

    // ── Coleta ───────────────────────────────────────────────────────────

    private List<VectorDocument> collect(UUID userId) {
        Map<String, String> categoryNames = new HashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            categoryNames.put(c.getId(), c.getNamePt());
        }
        Map<UUID, String> accountNames = new HashMap<>();
        List<Account> userAccounts = accounts.findAllByUserIdOrderByNameAsc(userId);
        for (Account a : userAccounts) {
            accountNames.put(a.getId(), a.getName());
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusMonths(INDEXED_MONTHS).withDayOfMonth(1);
        LocalDate to = today.plusMonths(2).withDayOfMonth(1);

        List<VectorDocument> docs = new ArrayList<>();

        for (Transaction t : transactions
                .findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(userId, from, to)) {
            String text = "Gasto: " + t.getDescription()
                    + " | valor " + money(t.getAmountCents())
                    + " | vencimento " + t.getDueDate()
                    + " | conta " + accountNames.getOrDefault(t.getAccountId(), "—")
                    + " | categoria " + categoryNames.getOrDefault(t.getCategoryId(), t.getCategoryId())
                    + " | " + (t.isPaid() ? "pago" : "em aberto")
                    + " | " + (t.isFixed() ? "fixo" : "pontual");
            docs.add(document(KIND_TRANSACTION, t.getId().toString(), text,
                    t.getDueDate(), t.getAmountCents()));
        }

        for (Income i : incomes
                .findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(userId, from, to)) {
            StringBuilder text = new StringBuilder("Receita: ").append(i.getSource())
                    .append(" | valor ").append(money(i.getAmountCents()))
                    .append(" | data ").append(i.getIncomeDate());
            if (i.getCategory() != null) {
                text.append(" | categoria ")
                    .append(categoryNames.getOrDefault(i.getCategory(), i.getCategory()));
            }
            if (i.getNote() != null && !i.getNote().isBlank()) {
                text.append(" | observação ").append(i.getNote());
            }
            docs.add(document(KIND_INCOME, i.getId().toString(), text.toString(),
                    i.getIncomeDate(), i.getAmountCents()));
        }

        for (Account a : userAccounts) {
            StringBuilder text = new StringBuilder("Conta/cartão: ").append(a.getName())
                    .append(" | tipo ").append(a.getKind());
            if (a.getLast4() != null && !a.getLast4().isBlank()) {
                text.append(" | final ").append(a.getLast4());
            }
            if (a.getDueDay() != null) {
                text.append(" | vence dia ").append(a.getDueDay());
            }
            docs.add(document(KIND_ACCOUNT, a.getId().toString(), text.toString(), null, null));
        }

        for (Investment inv : investments.findAllByUserIdOrderByNameAsc(userId)) {
            StringBuilder text = new StringBuilder("Investimento: ").append(inv.getName())
                    .append(" | tipo ").append(inv.getKind())
                    .append(" | aplicado ").append(money(inv.getAmountCents()));
            if (inv.getGoalCents() != null) {
                text.append(" | meta ").append(money(inv.getGoalCents()));
            }
            if (inv.getRateLabel() != null && !inv.getRateLabel().isBlank()) {
                text.append(" | rendimento ").append(inv.getRateLabel());
            }
            docs.add(document(KIND_INVESTMENT, inv.getId().toString(), text.toString(),
                    null, inv.getAmountCents()));
        }

        return docs;
    }

    private VectorDocument document(String kind, String refId, String rawText,
                                    LocalDate occurredOn, Long amountCents) {
        String text = truncate(rawText, props.getEmbedding().getMaxChars());
        return new VectorDocument(kind, refId, text, sha256(text), occurredOn, amountCents);
    }

    // ── Utilitários ──────────────────────────────────────────────────────

    private static String truncate(String text, int max) {
        if (max <= 0 || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private static String money(long cents) {
        return InsightFactsBuilder.money(BigDecimal.valueOf(cents, 2));
    }

    /** SHA-256 em hexa: a impressão digital que evita re-embeddar texto igual. */
    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}

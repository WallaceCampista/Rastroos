package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.domain.repository.CategoryRepository;
import com.rastroos.domain.repository.IncomeRepository;
import com.rastroos.domain.repository.InvestmentRepository;
import com.rastroos.domain.repository.TransactionRepository;
import com.rastroos.domain.repository.VectorStoreRepository;

/**
 * Índice semântico. O ponto sensível é o custo: reindexar precisa ser
 * <strong>incremental</strong> — quem tem mil lançamentos e edita um deve pagar
 * um embedding, não mil.
 */
@ExtendWith(MockitoExtension.class)
class VectorIndexServiceTest {

    @Mock private TransactionRepository transactions;
    @Mock private IncomeRepository incomes;
    @Mock private AccountRepository accounts;
    @Mock private InvestmentRepository investments;
    @Mock private CategoryRepository categories;
    @Mock private VectorStoreRepository store;
    @Mock private EmbeddingService embeddings;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
    private static final String MODEL = "text-embedding-3-small";

    private final UUID alice = UUID.randomUUID();

    private AiProperties props;
    private VectorIndexService service;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        service = new VectorIndexService(transactions, incomes, accounts, investments,
                categories, store, embeddings, props, clock);
    }

    @Test
    void embeddingsDesligados_naoFazNada() {
        when(embeddings.isEnabled()).thenReturn(false);

        assertThat(service.reindex(alice)).isZero();
        verifyNoInteractions(store);
    }

    @Test
    void primeiraIndexacao_vetorizaLancamentoEConta() {
        enableWithOneTransaction();
        when(store.fingerprintsByUser(alice)).thenReturn(Map.of());
        when(embeddings.embedAll(eq(alice), anyList()))
                .thenReturn(List.of(new float[] {0.1f}, new float[] {0.2f}));
        when(embeddings.modelName()).thenReturn("text-embedding-3-small");

        assertThat(service.reindex(alice)).isEqualTo(2);

        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(embeddings).embedAll(eq(alice), texts.capture());
        assertThat(texts.getValue()).hasSize(2);
        assertThat(texts.getValue().get(0)).contains("Padaria").contains("Cartão Teste");
    }

    @Test
    void nadaMudou_naoGeraNenhumEmbedding() {
        // O índice já contém exatamente o que seria produzido agora.
        Map<String, String> indexed = currentlyIndexed();
        when(store.fingerprintsByUser(alice)).thenReturn(indexed);
        when(embeddings.modelName()).thenReturn(MODEL);
        enableWithOneTransaction();

        assertThat(service.reindex(alice)).isZero();

        verify(embeddings, never()).embedAll(any(), anyList());
        verify(store, never()).upsertAll(any(), anyList(), anyList(), anyString());
    }

    @Test
    void umTextoMudou_vetorizaSoEle_naoOIndiceInteiro() {
        Map<String, String> indexed = new java.util.HashMap<>(currentlyIndexed());
        indexed.put(txKey(), "hash-antigo");   // só o lançamento mudou
        when(store.fingerprintsByUser(alice)).thenReturn(indexed);
        enableWithOneTransaction();
        when(embeddings.embedAll(eq(alice), anyList())).thenReturn(List.of(new float[] {0.1f}));
        when(embeddings.modelName()).thenReturn("text-embedding-3-small");

        assertThat(service.reindex(alice)).isEqualTo(1);

        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(embeddings).embedAll(eq(alice), texts.capture());
        assertThat(texts.getValue()).hasSize(1);
        assertThat(texts.getValue().get(0)).contains("Padaria");
    }

    @Test
    void linhaQueSumiu_ehRemovidaDoIndice() {
        Map<String, String> indexed = new java.util.HashMap<>(currentlyIndexed());
        indexed.put("TRANSACTION|apagada", "x");
        when(store.fingerprintsByUser(alice)).thenReturn(indexed);
        when(embeddings.modelName()).thenReturn(MODEL);
        enableWithOneTransaction();

        service.reindex(alice);

        ArgumentCaptor<Set<String>> removed = ArgumentCaptor.forClass(Set.class);
        verify(store).deleteByKeys(eq(alice), removed.capture());
        assertThat(removed.getValue()).containsExactly("TRANSACTION|apagada");
    }

    /**
     * Vetor de outro fornecedor vive em outro espaço: mesmo com o texto
     * intacto, trocar de motor precisa reindexar — senão a busca semântica
     * compara vetores incomparáveis sem erro nenhum.
     */
    @Test
    void trocaDeFornecedorReindexaMesmoSemMudancaDeTexto() {
        Map<String, String> indexed = currentlyIndexed();
        when(store.fingerprintsByUser(alice)).thenReturn(indexed);
        when(embeddings.modelName()).thenReturn("gemini-embedding-001");
        when(embeddings.embedAll(eq(alice), anyList()))
                .thenReturn(List.of(new float[] {0.1f}, new float[] {0.2f}));
        enableWithOneTransaction();

        assertThat(service.reindex(alice)).isEqualTo(2);

        verify(store).upsertAll(eq(alice), anyList(), anyList(), eq("gemini-embedding-001"));
    }

    @Test
    void purgeApagaTudoDoUsuario() {
        when(store.deleteAllByUser(alice)).thenReturn(42);

        assertThat(service.purge(alice)).isEqualTo(42);
    }

    @Test
    void textoIndexadoRespeitaOTetoDeCaracteres() {
        props.getEmbedding().setMaxChars(20);
        enableWithOneTransaction();
        when(store.fingerprintsByUser(alice)).thenReturn(Map.of());
        when(embeddings.embedAll(eq(alice), anyList()))
                .thenReturn(List.of(new float[] {0.1f}, new float[] {0.2f}));
        when(embeddings.modelName()).thenReturn("m");

        service.reindex(alice);

        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(embeddings).embedAll(eq(alice), texts.capture());
        assertThat(texts.getValue().get(0)).hasSize(20);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static final UUID TX_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private void enableWithOneTransaction() {
        when(embeddings.isEnabled()).thenReturn(true);
        when(categories.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        Account account = new Account();
        account.setId(ACC_ID);
        account.setUserId(alice);
        account.setName("Cartão Teste");
        account.setKind(AccountKind.CARD);
        when(accounts.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of(account));

        Transaction tx = new Transaction();
        tx.setId(TX_ID);
        tx.setUserId(alice);
        tx.setAccountId(ACC_ID);
        tx.setCategoryId("alimentacao");
        tx.setDescription("Padaria");
        tx.setAmountCents(1250L);
        tx.setDueDate(LocalDate.of(2026, 9, 10));
        when(transactions.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(eq(alice), any(), any()))
                .thenReturn(List.of(tx));
        when(incomes.findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(eq(alice), any(), any()))
                .thenReturn(List.of());
        when(investments.findAllByUserIdOrderByNameAsc(alice)).thenReturn(List.of());
    }

    private String txKey() {
        return "TRANSACTION|" + TX_ID;
    }

    /**
     * O que o índice conteria se já estivesse em dia. Derivado de uma execução
     * real com índice vazio, em vez de hashes escritos à mão — assim o teste
     * não quebra quando o formato do texto indexado muda, só quando o
     * comportamento incremental quebra, que é o que ele existe para proteger.
     */
    private Map<String, String> currentlyIndexed() {
        VectorStoreRepository probeStore = org.mockito.Mockito.mock(VectorStoreRepository.class);
        EmbeddingService probeEmbeddings = org.mockito.Mockito.mock(EmbeddingService.class);
        when(probeStore.fingerprintsByUser(alice)).thenReturn(Map.of());
        when(probeEmbeddings.isEnabled()).thenReturn(true);
        when(probeEmbeddings.embedAll(eq(alice), anyList()))
                .thenReturn(List.of(new float[] {0f}, new float[] {0f}));
        when(probeEmbeddings.modelName()).thenReturn(MODEL);

        VectorIndexService probe = new VectorIndexService(transactions, incomes, accounts,
                investments, categories, probeStore, probeEmbeddings, props, clock);
        enableWithOneTransaction();
        probe.reindex(alice);

        ArgumentCaptor<List<VectorDocument>> docs = ArgumentCaptor.forClass(List.class);
        verify(probeStore).upsertAll(eq(alice), docs.capture(), anyList(), anyString());
        Map<String, String> fingerprints = new java.util.HashMap<>();
        for (VectorDocument d : docs.getValue()) {
            fingerprints.put(d.key(),
                    VectorStoreRepository.fingerprint(d.contentHash(), MODEL));
        }
        return fingerprints;
    }
}

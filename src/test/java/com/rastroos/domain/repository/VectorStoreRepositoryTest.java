package com.rastroos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.rastroos.domain.service.VectorDocument;
import com.rastroos.domain.service.VectorMatch;

/**
 * Índice vetorial contra o Postgres real com pgvector. Cobre o que só o banco
 * pode provar: o tipo {@code vector}, o operador de distância de cosseno e —
 * o mais importante — que a busca <strong>nunca cruza usuários</strong> (§2.2).
 */
@Import(VectorStoreRepository.class)
class VectorStoreRepositoryTest extends RepositoryTestBase {

    private static final int DIM = 1536;

    @Autowired private VectorStoreRepository store;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void gravaEEncontraOVizinhoMaisProximo() {
        store.upsertAll(alice,
                List.of(doc("TRANSACTION", "1", "Gasto: Farmácia"),
                        doc("TRANSACTION", "2", "Gasto: Posto de gasolina")),
                List.of(unit(0), unit(1)),
                "modelo-teste");

        List<VectorMatch> found = store.search(alice, unit(0), 5);

        assertThat(found).isNotEmpty();
        assertThat(found.get(0).content()).contains("Farmácia");
        // Vetor idêntico → distância zero → similaridade 1.
        assertThat(found.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(found.get(0).kind()).isEqualTo("TRANSACTION");
        assertThat(found.get(0).amountCents()).isEqualTo(8790L);
        assertThat(found.get(0).occurredOn()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void buscaNaoCruzaUsuarios() {
        store.upsertAll(alice, List.of(doc("TRANSACTION", "1", "Gasto da Alice")),
                List.of(unit(0)), "m");
        store.upsertAll(bob, List.of(doc("TRANSACTION", "1", "Gasto do Bob")),
                List.of(unit(0)), "m");

        List<VectorMatch> aliceSees = store.search(alice, unit(0), 10);
        List<VectorMatch> bobSees = store.search(bob, unit(0), 10);

        assertThat(aliceSees).hasSize(1);
        assertThat(aliceSees.get(0).content()).isEqualTo("Gasto da Alice");
        assertThat(bobSees).hasSize(1);
        assertThat(bobSees.get(0).content()).isEqualTo("Gasto do Bob");
    }

    @Test
    void reindexarOMesmoDocumentoAtualizaEmVezDeDuplicar() {
        store.upsertAll(alice, List.of(doc("TRANSACTION", "1", "Descrição antiga")),
                List.of(unit(0)), "m");
        store.upsertAll(alice, List.of(doc("TRANSACTION", "1", "Descrição nova")),
                List.of(unit(0)), "m");

        List<VectorMatch> found = store.search(alice, unit(0), 10);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).content()).isEqualTo("Descrição nova");
    }

    @Test
    void impressoesDigitaisVoltamPorChave() {
        store.upsertAll(alice,
                List.of(doc("TRANSACTION", "1", "a"), doc("ACCOUNT", "9", "b")),
                List.of(unit(0), unit(1)), "m");

        Map<String, String> fingerprints = store.fingerprintsByUser(alice);

        assertThat(fingerprints).containsOnlyKeys("TRANSACTION|1", "ACCOUNT|9");
        // A impressão carrega o modelo junto do hash do texto.
        assertThat(fingerprints.get("TRANSACTION|1")).isEqualTo("hash-TRANSACTION-1@m");
    }

    @Test
    void remocaoPorChaveApagaSoODocumentoIndicado() {
        store.upsertAll(alice,
                List.of(doc("TRANSACTION", "1", "fica"), doc("TRANSACTION", "2", "sai")),
                List.of(unit(0), unit(1)), "m");

        assertThat(store.deleteByKeys(alice, Set.of("TRANSACTION|2"))).isEqualTo(1);

        assertThat(store.fingerprintsByUser(alice)).containsOnlyKeys("TRANSACTION|1");
    }

    @Test
    void chaveMalFormadaEhIgnoradaSemApagarNadaAMais() {
        store.upsertAll(alice, List.of(doc("TRANSACTION", "1", "fica")), List.of(unit(0)), "m");

        assertThat(store.deleteByKeys(alice, Set.of("sem-separador"))).isZero();
        assertThat(store.fingerprintsByUser(alice)).hasSize(1);
    }

    @Test
    void purgeApagaSoOsDocumentosDoUsuario() {
        store.upsertAll(alice, List.of(doc("TRANSACTION", "1", "a")), List.of(unit(0)), "m");
        store.upsertAll(bob, List.of(doc("TRANSACTION", "1", "b")), List.of(unit(0)), "m");

        assertThat(store.deleteAllByUser(alice)).isEqualTo(1);

        assertThat(store.fingerprintsByUser(alice)).isEmpty();
        assertThat(store.fingerprintsByUser(bob)).hasSize(1);
    }

    @Test
    void listaVaziaNaoFazNada() {
        store.upsertAll(alice, List.of(), List.of(), "m");

        assertThat(store.fingerprintsByUser(alice)).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static VectorDocument doc(String kind, String refId, String content) {
        return new VectorDocument(kind, refId, content, "hash-" + kind + "-" + refId,
                LocalDate.of(2026, 9, 10), 8790L);
    }

    /** Vetor unitário na posição {@code axis}: distâncias previsíveis. */
    private static float[] unit(int axis) {
        float[] vector = new float[DIM];
        vector[axis] = 1f;
        return vector;
    }
}

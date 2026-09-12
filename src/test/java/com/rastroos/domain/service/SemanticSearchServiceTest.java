package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.repository.VectorStoreRepository;

/**
 * Busca semântica. Duas garantias importam: trecho pouco parecido não entra no
 * contexto (ruído vira alucinação) e falha na busca nunca derruba a pergunta —
 * o dossiê exato sozinho já responde a maioria delas.
 */
@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    @Mock private VectorStoreRepository store;
    @Mock private EmbeddingService embeddings;

    private final UUID alice = UUID.randomUUID();
    private AiProperties props;
    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        service = new SemanticSearchService(store, embeddings, props);
    }

    @Test
    void semEmbeddings_devolveVazioSemConsultarNada() {
        when(embeddings.isEnabled()).thenReturn(false);

        assertThat(service.search(alice, "farmácia")).isEmpty();
        verifyNoInteractions(store);
    }

    @Test
    void perguntaVazia_naoGeraVetorNemConsulta() {
        when(embeddings.isEnabled()).thenReturn(true);

        assertThat(service.search(alice, "   ")).isEmpty();
        assertThat(service.search(alice, null)).isEmpty();

        verifyNoInteractions(store);
        verify(embeddings, never()).embedOne(any(), anyString());
    }

    @Test
    void filtraTrechosAbaixoDaSimilaridadeMinima() {
        props.getEmbedding().setMinScore(0.5);
        when(embeddings.isEnabled()).thenReturn(true);
        when(embeddings.embedOne(alice, "farmácia")).thenReturn(new float[] {0.1f});
        when(store.search(any(), any(), anyInt())).thenReturn(List.of(
                match("Gasto: Drogaria", 0.81),
                match("Gasto: Posto de gasolina", 0.22)));

        List<VectorMatch> found = service.search(alice, "farmácia");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).content()).contains("Drogaria");
    }

    @Test
    void falhaNaBuscaNaoPropaga_aPerguntaSegueComOsDadosExatos() {
        when(embeddings.isEnabled()).thenReturn(true);
        when(embeddings.embedOne(any(), anyString()))
                .thenThrow(new AiUnavailableException("sem crédito"));

        assertThat(service.search(alice, "farmácia")).isEmpty();
    }

    @Test
    void semTrechos_aSecaoInteiraSomeDoPrompt() {
        assertThat(service.asPromptSection(List.of())).isEmpty();
    }

    @Test
    void comTrechos_aSecaoAvisaQueNaoSaoBaseDeCalculo() {
        String section = service.asPromptSection(List.of(match("Gasto: Drogaria", 0.9)));

        assertThat(section)
                .contains("Drogaria")
                .contains("NÃO são um total")
                .contains("não some nem conte");
    }

    private static VectorMatch match(String content, double score) {
        return new VectorMatch("TRANSACTION", UUID.randomUUID().toString(), content,
                LocalDate.of(2026, 8, 12), 8790L, score);
    }
}

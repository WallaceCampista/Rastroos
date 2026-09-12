package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Vetorização. O lote existe por economia: o fornecedor cobra por token, não
 * por requisição — mandar 64 frases de uma vez custa o mesmo que 64 chamadas
 * e gasta uma fração do tempo.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock private AiModelClient client;
    @Mock private AiBudgetGuard budget;

    private final UUID alice = UUID.randomUUID();
    private AiProperties props;
    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        service = new EmbeddingService(client, budget,
                new AiCircuitBreakers(CircuitBreakerRegistry.ofDefaults()), props);
    }

    @Test
    void desligadoPorConfiguracao_naoChamaNada() {
        props.getEmbedding().setEnabled(false);

        assertThatThrownBy(() -> service.embedAll(alice, List.of("a")))
                .isInstanceOf(AiUnavailableException.class);
        verifyNoInteractions(client, budget);
    }

    @Test
    void listaVazia_naoConsomeOrcamento() {
        when(client.isEnabled()).thenReturn(true);

        assertThat(service.embedAll(alice, List.of())).isEmpty();
        verifyNoInteractions(budget);
    }

    @Test
    void divideEmLotesDoTamanhoConfigurado() {
        props.getEmbedding().setBatchSize(2);
        when(client.isEnabled()).thenReturn(true);
        when(client.embed(any(), anyList()))
                .thenReturn(new AiEmbeddings(List.of(new float[] {1f}, new float[] {2f}),
                        AiTokenUsage.ZERO))
                .thenReturn(new AiEmbeddings(List.of(new float[] {3f}), AiTokenUsage.ZERO));

        List<float[]> vectors = service.embedAll(alice, List.of("a", "b", "c"));

        assertThat(vectors).hasSize(3);
        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(client, times(2)).embed(any(), batches.capture());
        assertThat(batches.getAllValues().get(0)).containsExactly("a", "b");
        assertThat(batches.getAllValues().get(1)).containsExactly("c");
    }

    @Test
    void verificaOTetoAntesDeGastar() {
        when(client.isEnabled()).thenReturn(true);
        when(client.embed(any(), anyList()))
                .thenReturn(new AiEmbeddings(List.of(new float[] {1f}), AiTokenUsage.ZERO));

        service.embedAll(alice, List.of("a"));

        verify(budget).check(alice, AiFeature.EMBEDDING);
    }

    @Test
    void embedOneDevolveOVetorDaPergunta() {
        when(client.isEnabled()).thenReturn(true);
        when(client.embed(any(), anyList()))
                .thenReturn(new AiEmbeddings(List.of(new float[] {0.5f}), AiTokenUsage.ZERO));

        assertThat(service.embedOne(alice, "farmácia")).containsExactly(0.5f);
    }

    @Test
    void fornecedorSemVetor_naoDevolveNuloSilenciosamente() {
        when(client.isEnabled()).thenReturn(true);
        when(client.embed(any(), anyList()))
                .thenReturn(new AiEmbeddings(List.of(), AiTokenUsage.ZERO));

        assertThatThrownBy(() -> service.embedOne(alice, "x"))
                .isInstanceOf(AiUnavailableException.class);
    }
}

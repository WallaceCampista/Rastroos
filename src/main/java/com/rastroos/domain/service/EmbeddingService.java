package com.rastroos.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;

/**
 * Vetorização de texto para a busca semântica.
 *
 * <p>Envia em lotes ({@code ai.embedding.batch-size}) porque o provedor cobra
 * por token e não por requisição: um lote de 64 frases custa o mesmo que 64
 * chamadas, e gasta 1/64 do tempo de rede.
 *
 * <p>Protegido pelo circuit breaker {@code ai-embedding}, separado do chat —
 * uma indexação problemática não pode calar o Alfredo.
 */
@Service
public class EmbeddingService {

    private final AiModelClient client;
    private final AiBudgetGuard budget;
    private final AiCircuitBreakers breakers;
    private final AiProperties props;

    public EmbeddingService(AiModelClient client, AiBudgetGuard budget,
                            AiCircuitBreakers breakers, AiProperties props) {
        this.client = client;
        this.budget = budget;
        this.breakers = breakers;
        this.props = props;
    }

    public boolean isEnabled() {
        return props.getEmbedding().isEnabled() && client.isEnabled();
    }

    /** Modelo de embedding em uso (configurado ou padrão do fornecedor). */
    public String modelName() {
        return client.embeddingModel();
    }

    /**
     * Vetores dos textos, na mesma ordem. Lança {@link AiUnavailableException}
     * quando a IA está desligada, sem orçamento ou fora do ar.
     */
    public List<float[]> embedAll(UUID userId, List<String> texts) {
        if (!isEnabled()) {
            throw new AiUnavailableException("Embeddings desabilitados");
        }
        if (texts.isEmpty()) {
            return List.of();
        }
        budget.check(userId, AiFeature.EMBEDDING);

        int batchSize = Math.max(1, props.getEmbedding().getBatchSize());
        List<float[]> out = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + batchSize));
            out.addAll(breakers.call(AiCircuitBreakers.EMBEDDING,
                    () -> client.embed(userId, batch).vectors()));
        }
        return out;
    }

    /** Vetor de um texto só (a pergunta do chat). */
    public float[] embedOne(UUID userId, String text) {
        List<float[]> result = embedAll(userId, List.of(text));
        if (result.isEmpty()) {
            throw new AiUnavailableException("Provedor não devolveu vetor");
        }
        return result.get(0);
    }
}

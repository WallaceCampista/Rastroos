package com.rastroos.domain.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.repository.VectorStoreRepository;

/**
 * Busca semântica sobre o índice vetorial do usuário.
 *
 * <p>Serve ao que a agregação por SQL não alcança: encontrar a linha certa a
 * partir de uma descrição vaga ("aquela compra da farmácia", "o boleto do
 * dentista"). O resultado entra no contexto como <em>pista</em>, sempre
 * identificado como tal — os totais continuam vindo do
 * {@link FinancialContextBuilder}, e o prompt deixa claro que somar trechos
 * recuperados não é resposta válida.
 *
 * <p>Falha de busca nunca derruba a pergunta: sem índice, sem orçamento ou com
 * o provedor fora do ar, devolve vazio e o Alfredo responde com o dossiê
 * exato, que já cobre a maioria das perguntas.
 */
@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final VectorStoreRepository store;
    private final EmbeddingService embeddings;
    private final AiProperties props;

    public SemanticSearchService(VectorStoreRepository store,
                                 EmbeddingService embeddings,
                                 AiProperties props) {
        this.store = store;
        this.embeddings = embeddings;
        this.props = props;
    }

    /** Trechos do usuário mais parecidos com a pergunta; vazio se não houver. */
    @Transactional(readOnly = true)
    public List<VectorMatch> search(UUID userId, String question) {
        if (!embeddings.isEnabled() || question == null || question.isBlank()) {
            return List.of();
        }
        try {
            float[] vector = embeddings.embedOne(userId, question.strip());
            double minScore = props.getEmbedding().getMinScore();
            return store.search(userId, vector, props.getEmbedding().getTopK()).stream()
                    .filter(m -> m.score() >= minScore)
                    .toList();
        } catch (RuntimeException e) {
            log.debug("Busca semântica indisponível ({}); seguindo só com os dados exatos",
                    e.toString());
            return List.of();
        }
    }

    /**
     * Formata os trechos para o prompt, ou {@code ""} quando não há nada
     * relevante — nesse caso a seção inteira some do contexto em vez de virar
     * um cabeçalho vazio que o modelo tentaria interpretar.
     */
    public String asPromptSection(List<VectorMatch> matches) {
        if (matches.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append("\n## Registros parecidos com a pergunta (busca por semelhança)\n");
        sb.append("Pistas para localizar o registro certo. NÃO são um total: ")
          .append("não some nem conte estes itens — para números use as seções acima.\n");
        for (VectorMatch m : matches) {
            sb.append("- ").append(m.content()).append('\n');
        }
        return sb.toString();
    }
}

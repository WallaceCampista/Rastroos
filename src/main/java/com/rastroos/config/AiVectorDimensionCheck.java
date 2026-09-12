package com.rastroos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.rastroos.domain.service.AiModelClient;

/**
 * Confere, no boot, se a dimensão dos vetores do fornecedor casa com a coluna
 * {@code ai_documents.embedding} ({@code vector(N)}).
 *
 * <p>Existe por causa da troca de fornecedor: cada um vetoriza numa dimensão
 * diferente ({@code text-embedding-3-small} = 1536, {@code text-embedding-004}
 * = 768…). Sem esta checagem, trocar de motor só daria erro na hora de gravar
 * o primeiro documento — em segundo plano, num log que ninguém lê. Aqui o
 * aviso sai no boot, dizendo exatamente o que fazer.
 *
 * <p>Não derruba a aplicação: busca semântica é acessório, e o resto do
 * Rastroo$ funciona sem ela.
 */
@Component
public class AiVectorDimensionCheck {

    private static final Logger log = LoggerFactory.getLogger(AiVectorDimensionCheck.class);

    private static final String COLUMN_DIMENSION = """
            SELECT a.atttypmod
              FROM pg_attribute a
              JOIN pg_class c ON c.oid = a.attrelid
             WHERE c.relname = 'ai_documents' AND a.attname = 'embedding'
            """;

    private final JdbcTemplate jdbc;
    private final AiModelClient ai;
    private final AiProperties props;

    public AiVectorDimensionCheck(JdbcTemplate jdbc, AiModelClient ai, AiProperties props) {
        this.jdbc = jdbc;
        this.ai = ai;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (!props.getEmbedding().isEnabled() || !ai.isEnabled()) {
            return;
        }
        Integer column;
        try {
            column = jdbc.queryForObject(COLUMN_DIMENSION, Integer.class);
        } catch (RuntimeException e) {
            log.debug("Não consegui ler a dimensão da coluna vetorial: {}", e.toString());
            return;
        }
        int expected = ai.embeddingDimensions();
        if (column == null || column <= 0 || column == expected) {
            return;
        }
        log.error("""
                Busca semântica desativada na prática: o modelo de embeddings produz vetores de \
                {} dimensões, mas ai_documents.embedding é vector({}).
                Como resolver, sem perder dado:
                  1) use um modelo que aceite {} dimensões (parâmetro `dimensions`), ou
                  2) crie um changeset Liquibase novo alterando a coluna para vector({}), \
                recriando o índice HNSW, e reindexe (a tabela é cache derivado: pode ser truncada).""",
                expected, column, column, expected);
    }
}

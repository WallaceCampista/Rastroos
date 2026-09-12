package com.rastroos.domain.repository;

import java.sql.Date;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.rastroos.domain.service.VectorDocument;
import com.rastroos.domain.service.VectorMatch;

/**
 * Acesso à tabela {@code ai_documents} (índice semântico do pgvector).
 *
 * <p><strong>Por que não é Spring Data JPA</strong> (§2.1): a coluna
 * {@code embedding} é do tipo {@code vector(1536)}, que o Hibernate não mapeia
 * nativamente, e a ordenação usa o operador de distância {@code <=>} do
 * pgvector, sem equivalente em JPQL. O SQL abaixo é <strong>sempre
 * parametrizado</strong> (§3.2/§3.3): nenhum valor de usuário entra por
 * concatenação — o vetor viaja como parâmetro e é convertido com
 * {@code CAST(? AS vector)}.
 *
 * <p>Toda consulta filtra por {@code user_id} (§2.2).
 */
@Repository
public class VectorStoreRepository {

    private static final String UPSERT = """
            INSERT INTO ai_documents (user_id, kind, ref_id, content, content_hash,
                                      occurred_on, amount_cents, model, updated_at, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), CAST(? AS vector))
            ON CONFLICT (user_id, kind, ref_id) DO UPDATE
               SET content      = EXCLUDED.content,
                   content_hash = EXCLUDED.content_hash,
                   occurred_on  = EXCLUDED.occurred_on,
                   amount_cents = EXCLUDED.amount_cents,
                   model        = EXCLUDED.model,
                   updated_at   = now(),
                   embedding    = EXCLUDED.embedding
            """;

    /** {@code <=>} é a distância de cosseno; a similaridade é o complemento. */
    private static final String SEARCH = """
            SELECT kind, ref_id, content, occurred_on, amount_cents,
                   1 - (embedding <=> CAST(? AS vector)) AS score
              FROM ai_documents
             WHERE user_id = ?
               AND embedding IS NOT NULL
             ORDER BY embedding <=> CAST(? AS vector)
             LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public VectorStoreRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Impressões digitais já indexadas, por {@code kind|refId}. */
    public Map<String, String> hashesByUser(UUID userId) {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT kind, ref_id, content_hash FROM ai_documents WHERE user_id = ?",
                rs -> {
                    out.put(rs.getString("kind") + '|' + rs.getString("ref_id"),
                            rs.getString("content_hash"));
                },
                userId);
        return out;
    }

    /** Grava (ou atualiza) documentos já vetorizados. */
    public void upsertAll(UUID userId, List<VectorDocument> docs, List<float[]> embeddings, String model) {
        if (docs.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            VectorDocument d = docs.get(i);
            batch.add(new Object[] {
                    userId, d.kind(), d.refId(), d.content(), d.contentHash(),
                    d.occurredOn() == null ? null : Date.valueOf(d.occurredOn()),
                    d.amountCents(), model, toVectorLiteral(embeddings.get(i))
            });
        }
        jdbc.batchUpdate(UPSERT, batch, new int[] {
                Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.DATE, Types.BIGINT, Types.VARCHAR, Types.VARCHAR
        });
    }

    /** Remove documentos cuja linha de origem sumiu (ou saiu da janela indexada). */
    public int deleteByKeys(UUID userId, Collection<String> kindAndRefIds) {
        int removed = 0;
        for (String key : kindAndRefIds) {
            int sep = key.indexOf('|');
            if (sep <= 0) {
                continue;
            }
            removed += jdbc.update(
                    "DELETE FROM ai_documents WHERE user_id = ? AND kind = ? AND ref_id = ?",
                    userId, key.substring(0, sep), key.substring(sep + 1));
        }
        return removed;
    }

    public int deleteAllByUser(UUID userId) {
        return jdbc.update("DELETE FROM ai_documents WHERE user_id = ?", userId);
    }

    /** Vizinhos mais próximos do vetor da pergunta, sempre dentro do usuário. */
    public List<VectorMatch> search(UUID userId, float[] query, int topK) {
        String literal = toVectorLiteral(query);
        return jdbc.query(SEARCH,
                (rs, rowNum) -> new VectorMatch(
                        rs.getString("kind"),
                        rs.getString("ref_id"),
                        rs.getString("content"),
                        rs.getObject("occurred_on", java.time.LocalDate.class),
                        rs.getObject("amount_cents") == null ? null : rs.getLong("amount_cents"),
                        rs.getDouble("score")),
                literal, userId, literal, Math.max(1, topK));
    }

    /** Formato textual aceito pelo pgvector: {@code [0.1,0.2,...]}. */
    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}

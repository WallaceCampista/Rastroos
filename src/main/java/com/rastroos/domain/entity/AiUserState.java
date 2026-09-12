package com.rastroos.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Estado da IA para um usuário: o contador que diz "algo mudou" e o que já foi
 * refletido em resumos e no índice vetorial.
 *
 * <p>{@link #dataVersion} sobe a cada escrita financeira (lançamento, receita,
 * conta, investimento). {@link #warmedVersion} e {@link #indexedVersion}
 * registram até onde o pré-aquecimento chegou. Enquanto forem iguais, não há
 * nada a regerar — e portanto nada a consumir.
 *
 * <p>{@link #dirtySince} é marcado na primeira escrita de uma rajada: o
 * varredor só age depois que o debounce vence, o que transforma dez
 * lançamentos seguidos numa geração só.
 */
@Entity
@Table(name = "ai_user_state")
public class AiUserState {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "data_version", nullable = false)
    private long dataVersion;

    @Column(name = "warmed_version", nullable = false)
    private long warmedVersion = -1L;

    @Column(name = "indexed_version", nullable = false)
    private long indexedVersion = -1L;

    @Column(name = "dirty_since")
    private Instant dirtySince;

    @Column(name = "last_warm_at")
    private Instant lastWarmAt;

    /** Última falha do aquecimento, truncada — diagnóstico, não conteúdo. */
    @Column(name = "last_warm_error", length = 300)
    private String lastWarmError;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public long getDataVersion() { return dataVersion; }
    public void setDataVersion(long dataVersion) { this.dataVersion = dataVersion; }

    public long getWarmedVersion() { return warmedVersion; }
    public void setWarmedVersion(long warmedVersion) { this.warmedVersion = warmedVersion; }

    public long getIndexedVersion() { return indexedVersion; }
    public void setIndexedVersion(long indexedVersion) { this.indexedVersion = indexedVersion; }

    public Instant getDirtySince() { return dirtySince; }
    public void setDirtySince(Instant dirtySince) { this.dirtySince = dirtySince; }

    public Instant getLastWarmAt() { return lastWarmAt; }
    public void setLastWarmAt(Instant lastWarmAt) { this.lastWarmAt = lastWarmAt; }

    public String getLastWarmError() { return lastWarmError; }
    public void setLastWarmError(String lastWarmError) { this.lastWarmError = lastWarmError; }
}

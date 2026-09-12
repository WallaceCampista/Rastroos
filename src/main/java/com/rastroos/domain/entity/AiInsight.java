package com.rastroos.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Resumo do Alfredo já gerado para uma tela, persistido.
 *
 * <p>A chave do reaproveitamento é o {@link #factsHash}: a impressão digital
 * (SHA-256) dos números que geraram o texto. Enquanto o hash bater, o texto é
 * servido do banco <strong>sem nenhuma chamada ao provedor</strong> — não
 * importa há quanto tempo foi gerado. É isso que impede o consumo diário de um
 * usuário que passou o mês sem lançar nada.
 *
 * <p>Tabela interna/derivada: pode ser truncada a qualquer momento e o sistema
 * apenas volta a gerar sob demanda.
 */
@Entity
@Table(name = "ai_insights")
public class AiInsight {

    /** Sentinela de período para telas que não dependem do mês. */
    public static final String NO_PERIOD = "-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "screen", nullable = false, length = 20)
    private String screen;

    /** {@code YYYY-MM} ou {@link #NO_PERIOD}. */
    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "facts_hash", nullable = false, length = 64)
    private String factsHash;

    @Column(name = "prompt_version", nullable = false)
    private int promptVersion;

    @Column(name = "model", nullable = false, length = 60)
    private String model;

    @Column(name = "summary_text", nullable = false, columnDefinition = "text")
    private String summaryText;

    /** {@code false} quando é o resumo local determinístico (IA indisponível). */
    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    /** Versão dos dados do usuário no momento da geração. */
    @Column(name = "data_version", nullable = false)
    private long dataVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getScreen() { return screen; }
    public void setScreen(String screen) { this.screen = screen; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getFactsHash() { return factsHash; }
    public void setFactsHash(String factsHash) { this.factsHash = factsHash; }

    public int getPromptVersion() { return promptVersion; }
    public void setPromptVersion(int promptVersion) { this.promptVersion = promptVersion; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public boolean isAiGenerated() { return aiGenerated; }
    public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }

    public long getDataVersion() { return dataVersion; }
    public void setDataVersion(long dataVersion) { this.dataVersion = dataVersion; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}

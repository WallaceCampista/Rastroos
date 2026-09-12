package com.rastroos.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.rastroos.domain.entity.enums.AiFeature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Livro-caixa de tokens: uma linha por chamada ao provedor de IA. É a base do
 * teto diário por usuário ({@code AiBudgetGuard}) e da leitura de custo.
 *
 * <p>Guarda apenas <em>contagem</em> — nunca o prompt, a resposta ou qualquer
 * valor monetário do usuário (§3.2).
 */
@Entity
@Table(name = "ai_usage")
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Nulo em chamadas sem dono (aquecimento administrativo). */
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 20)
    private AiFeature feature;

    @Column(name = "model", nullable = false, length = 60)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    /** Dia UTC — chave do teto diário, sem depender do fuso do usuário. */
    @Column(name = "usage_day", nullable = false)
    private LocalDate usageDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public AiFeature getFeature() { return feature; }
    public void setFeature(AiFeature feature) { this.feature = feature; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public LocalDate getUsageDay() { return usageDay; }
    public void setUsageDay(LocalDate usageDay) { this.usageDay = usageDay; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

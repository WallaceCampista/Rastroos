package com.rastroos.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.rastroos.domain.entity.enums.InvestmentKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "investments")
public class Investment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private InvestmentKind kind;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "goal_cents")
    private Long goalCents;

    @Column(name = "rate_label", length = 60)
    private String rateLabel;

    @Column(name = "monthly_return_cents")
    private Long monthlyReturnCents;

    /** Pode ser cor sólida (#aabbcc) ou expressão CSS (linear-gradient(...)). */
    @Column(name = "color_hex", length = 80)
    private String colorHex;

    @Column(name = "icon_text", length = 8)
    private String iconText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Investment() {
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public InvestmentKind getKind() { return kind; }
    public void setKind(InvestmentKind kind) { this.kind = kind; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public Long getGoalCents() { return goalCents; }
    public void setGoalCents(Long goalCents) { this.goalCents = goalCents; }

    public String getRateLabel() { return rateLabel; }
    public void setRateLabel(String rateLabel) { this.rateLabel = rateLabel; }

    public Long getMonthlyReturnCents() { return monthlyReturnCents; }
    public void setMonthlyReturnCents(Long monthlyReturnCents) { this.monthlyReturnCents = monthlyReturnCents; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getIconText() { return iconText; }
    public void setIconText(String iconText) { this.iconText = iconText; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Investment other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Investment{id=" + id + ", userId=" + userId + ", name=" + name
                + ", kind=" + kind + ", amountCents=" + amountCents + "}";
    }
}

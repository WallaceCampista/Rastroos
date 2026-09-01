package com.rastroos.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.rastroos.domain.entity.enums.InvestmentMovementKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Livro-razão de um investimento: uma linha por evento (saldo inicial, aporte
 * ou resgate), com o valor do evento e o saldo resultante. Diferente de
 * {@link InvestmentHistory} (snapshot mensal do saldo), aqui cada lançamento é
 * uma linha própria — é o que alimenta as "Movimentações" do detalhe.
 */
@Entity
@Table(name = "investment_movement")
public class InvestmentMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "investment_id", nullable = false)
    private UUID investmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private InvestmentMovementKind kind;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "balance_after_cents", nullable = false)
    private long balanceAfterCents;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public InvestmentMovement() {
    }

    public InvestmentMovement(UUID investmentId, InvestmentMovementKind kind,
                              long amountCents, long balanceAfterCents, Instant occurredAt) {
        this.investmentId = investmentId;
        this.kind = kind;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getInvestmentId() { return investmentId; }
    public void setInvestmentId(UUID investmentId) { this.investmentId = investmentId; }

    public InvestmentMovementKind getKind() { return kind; }
    public void setKind(InvestmentMovementKind kind) { this.kind = kind; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public long getBalanceAfterCents() { return balanceAfterCents; }
    public void setBalanceAfterCents(long balanceAfterCents) { this.balanceAfterCents = balanceAfterCents; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestmentMovement other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

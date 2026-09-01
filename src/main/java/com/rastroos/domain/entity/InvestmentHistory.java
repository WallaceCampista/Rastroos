package com.rastroos.domain.entity;

import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "investment_history")
public class InvestmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "investment_id", nullable = false)
    private UUID investmentId;

    /** Formato "YYYY-MM". CHAR(7) no schema. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    /** Dinheiro novo aportado neste mês (soma dos aportes), independente do saldo. */
    @Column(name = "contributed_cents", nullable = false)
    private long contributedCents;

    public InvestmentHistory() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getInvestmentId() { return investmentId; }
    public void setInvestmentId(UUID investmentId) { this.investmentId = investmentId; }

    public String getYearMonth() { return yearMonth; }
    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public long getContributedCents() { return contributedCents; }
    public void setContributedCents(long contributedCents) { this.contributedCents = contributedCents; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestmentHistory other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "InvestmentHistory{id=" + id + ", investmentId=" + investmentId
                + ", yearMonth=" + yearMonth + ", amountCents=" + amountCents + "}";
    }
}

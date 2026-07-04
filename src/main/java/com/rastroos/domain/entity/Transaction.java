package com.rastroos.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id", nullable = false, length = 40)
    private String categoryId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    /** Em centavos. Restrição CHECK no banco exige > 0. */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "is_fixed", nullable = false)
    private boolean fixed;

    @Column(name = "is_paid", nullable = false)
    private boolean paid;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "installment_current")
    private Short installmentCurrent;

    @Column(name = "installment_total")
    private Short installmentTotal;

    @Column(name = "ends_at")
    private LocalDate endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Transaction() {
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

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public boolean isFixed() { return fixed; }
    public void setFixed(boolean fixed) { this.fixed = fixed; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Short getInstallmentCurrent() { return installmentCurrent; }
    public void setInstallmentCurrent(Short installmentCurrent) { this.installmentCurrent = installmentCurrent; }

    public Short getInstallmentTotal() { return installmentTotal; }
    public void setInstallmentTotal(Short installmentTotal) { this.installmentTotal = installmentTotal; }

    public LocalDate getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDate endsAt) { this.endsAt = endsAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Transaction{id=" + id + ", userId=" + userId + ", description=" + description
                + ", amountCents=" + amountCents + ", dueDate=" + dueDate + ", paid=" + paid + "}";
    }
}

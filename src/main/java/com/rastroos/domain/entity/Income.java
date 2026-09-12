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
@Table(name = "incomes")
public class Income {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source", nullable = false, length = 120)
    private String source;

    /** Fonte recorrente que gerou este lançamento; NULL numa receita avulsa. */
    @Column(name = "source_id")
    private UUID sourceId;

    /** Em centavos. CHECK > 0 no banco. */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "income_date", nullable = false)
    private LocalDate incomeDate;

    @Column(name = "category", length = 40)
    private String category;

    @Column(name = "note", length = 200)
    private String note;

    /**
     * Se o dinheiro já caiu. Um recebimento de receita fixa nasce
     * {@code false} — foi programado, não recebido — e só entra nos totais
     * de "recebido" depois de confirmado.
     */
    @Column(name = "received", nullable = false)
    private boolean received;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Income() {
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

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public LocalDate getIncomeDate() { return incomeDate; }
    public void setIncomeDate(LocalDate incomeDate) { this.incomeDate = incomeDate; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isReceived() { return received; }
    public void setReceived(boolean received) { this.received = received; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Income other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Income{id=" + id + ", userId=" + userId + ", source=" + source
                + ", amountCents=" + amountCents + ", incomeDate=" + incomeDate + "}";
    }
}

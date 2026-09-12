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

/**
 * Fonte de receita recorrente — na prática, a empresa que paga o salário.
 *
 * <p>Guarda o combinado ("a Acme paga R$ 5.000 todo dia 5"); os recebimentos
 * em si ficam em {@link Income}, uma linha por mês apontando para esta fonte.
 * Materializar em vez de projetar mantém dashboard, relatórios e IA somando
 * uma única tabela.
 */
@Entity
@Table(name = "income_sources")
public class IncomeSource {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Valor combinado, em centavos. CHECK > 0 no banco. */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    /**
     * Em qual <b>dia útil</b> do mês o pagamento cai (1–23). Não é o dia do
     * calendário: "5º dia útil" muda de data a cada mês conforme fim de
     * semana e feriado — quem resolve isso é o {@link BusinessDayCalendar}.
     */
    @Column(name = "pay_business_day", nullable = false)
    private short payBusinessDay;

    @Column(name = "note", length = 200)
    private String note;

    /** Preenchido quando a fonte é encerrada: nada novo é gerado a partir daí. */
    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IncomeSource() {
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

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public short getPayBusinessDay() { return payBusinessDay; }
    public void setPayBusinessDay(short payBusinessDay) { this.payBusinessDay = payBusinessDay; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDate getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDate closedAt) { this.closedAt = closedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncomeSource other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "IncomeSource{id=" + id + ", userId=" + userId + ", name=" + name
                + ", amountCents=" + amountCents + ", payBusinessDay=" + payBusinessDay + "}";
    }
}

package com.rastroos.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.rastroos.domain.entity.enums.AccountKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private AccountKind kind;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "icon_text", length = 8)
    private String iconText;

    /** Últimos 4 dígitos impressos no cartão (só para {@code kind = CARD}). */
    @Column(name = "last4", length = 4)
    private String last4;

    @Column(name = "close_day")
    private Short closeDay;

    @Column(name = "due_day")
    private Short dueDay;

    @Column(name = "category_id", length = 40)
    private String categoryId;

    @Column(name = "is_fixed", nullable = false)
    private boolean fixed;

    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Account() {
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

    public AccountKind getKind() { return kind; }
    public void setKind(AccountKind kind) { this.kind = kind; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getIconText() { return iconText; }
    public void setIconText(String iconText) { this.iconText = iconText; }

    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }

    public Short getCloseDay() { return closeDay; }
    public void setCloseDay(Short closeDay) { this.closeDay = closeDay; }

    public Short getDueDay() { return dueDay; }
    public void setDueDay(Short dueDay) { this.dueDay = dueDay; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public boolean isFixed() { return fixed; }
    public void setFixed(boolean fixed) { this.fixed = fixed; }

    public LocalDate getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDate closedAt) { this.closedAt = closedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Account{id=" + id + ", userId=" + userId + ", name=" + name + ", kind=" + kind + "}";
    }
}

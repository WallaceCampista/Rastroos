package com.rastroos.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.rastroos.domain.entity.enums.AccessorRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Solicitação, feita por um titular, para que o admin crie uma conta acessor
 * com acesso aos dados dele. O admin aprova (cria a conta e vincula em
 * {@link #createdUserId}) ou rejeita. {@code requesterUserId} é sempre o
 * titular-alvo — a conta acessor resultante terá {@code accessesUserId} igual
 * a este valor.
 */
@Entity
@Table(name = "accessor_requests")
public class AccessorRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "requester_user_id", nullable = false, updatable = false)
    private UUID requesterUserId;

    @Column(name = "accessor_name", nullable = false, length = 120)
    private String accessorName;

    @Column(name = "accessor_email", nullable = false, length = 180)
    private String accessorEmail;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccessorRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /** Conta acessor criada quando a solicitação é aprovada. */
    @Column(name = "created_user_id")
    private UUID createdUserId;

    public AccessorRequest() {
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = AccessorRequestStatus.PENDING;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(UUID requesterUserId) { this.requesterUserId = requesterUserId; }

    public String getAccessorName() { return accessorName; }
    public void setAccessorName(String accessorName) { this.accessorName = accessorName; }

    public String getAccessorEmail() { return accessorEmail; }
    public void setAccessorEmail(String accessorEmail) { this.accessorEmail = accessorEmail; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public AccessorRequestStatus getStatus() { return status; }
    public void setStatus(AccessorRequestStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }

    public UUID getCreatedUserId() { return createdUserId; }
    public void setCreatedUserId(UUID createdUserId) { this.createdUserId = createdUserId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessorRequest other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "AccessorRequest{id=" + id + ", requesterUserId=" + requesterUserId
                + ", status=" + status + "}";
    }
}

package com.rastroos.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;
import com.rastroos.domain.entity.enums.SupportTicketStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    /** Formato esperado "T-XXXX". Atribuído pelo Service. */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 20)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private SupportTicketCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private SupportTicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportTicketStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SupportTicket() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = SupportTicketStatus.OPEN;
        if (priority == null) priority = SupportTicketPriority.MEDIUM;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public SupportTicketCategory getCategory() { return category; }
    public void setCategory(SupportTicketCategory category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SupportTicketPriority getPriority() { return priority; }
    public void setPriority(SupportTicketPriority priority) { this.priority = priority; }

    public SupportTicketStatus getStatus() { return status; }
    public void setStatus(SupportTicketStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupportTicket other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "SupportTicket{id=" + id + ", userId=" + userId + ", category=" + category
                + ", priority=" + priority + ", status=" + status + "}";
    }
}

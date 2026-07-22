package com.forumx.support.ticket.entity;

import java.time.LocalDateTime;

import com.forumx.auth.entity.User;
import com.forumx.common.entity.BaseEntity;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/** Private customer-support ticket aggregate, isolated from community questions. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tickets_created_by", columnList = "created_by"),
        @Index(name = "idx_tickets_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_tickets_status", columnList = "status"),
        @Index(name = "idx_tickets_priority", columnList = "priority")
})
@AttributeOverrides({
        @AttributeOverride(name = "createdBy", column = @Column(name = "audit_created_by", updatable = false))
})
public class Ticket extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_tenant"))
    private Tenant tenant;

    /** Named creator to avoid colliding with BaseEntity's audit createdBy property. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_creator"))
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to", foreignKey = @ForeignKey(name = "fk_tickets_assignee"))
    private User assignedTo;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String subject;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketPriority priority;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * Business rule to check if the ticket is in a state that can accept messages.
     * In V17, tickets in CLOSED status reject new messages.
     */
    public boolean canAcceptMessages() {
        return this.status != TicketStatus.CLOSED;
    }

    /**
     * Registers a new message in the ticket conversation, updating the ticket's metadata.
     */
    public void registerNewMessage(TicketMessage message) {
        if (message.getCreatedAt() != null) {
            this.lastMessageAt = LocalDateTime.ofInstant(message.getCreatedAt(), java.time.ZoneOffset.UTC);
        } else {
            this.lastMessageAt = LocalDateTime.now();
        }
    }
}

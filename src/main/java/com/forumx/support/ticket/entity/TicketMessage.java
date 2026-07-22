package com.forumx.support.ticket.entity;

import java.util.UUID;
import com.forumx.auth.entity.User;
import com.forumx.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@Entity
@Table(name = "ticket_messages", indexes = {
        @Index(name = "idx_ticket_messages_ticket", columnList = "ticket_id"),
        @Index(name = "idx_ticket_messages_sender", columnList = "sender_id"),
        @Index(name = "idx_ticket_messages_ticket_created", columnList = "ticket_id, created_at"),
        @Index(name = "idx_ticket_messages_uuid", columnList = "message_uuid", unique = true)
})
@AttributeOverrides({
        @AttributeOverride(name = "createdBy", column = @Column(name = "audit_created_by", updatable = false))
})
public class TicketMessage extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ticket_messages_ticket"))
    private Ticket ticket;

    // TODO: Later support other sender types (e.g. AI Assistant, System, Moderator, Customer) as this supports expansion.
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ticket_messages_sender"))
    private User sender;

    @NotBlank
    @Size(min = 2, max = 5000)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Column(name = "internal_note", nullable = false)
    private boolean internalNote = false;

    @NotNull
    @Column(name = "message_uuid", nullable = false, updatable = false, unique = true)
    @Builder.Default
    private UUID messageUuid = UUID.randomUUID();
}

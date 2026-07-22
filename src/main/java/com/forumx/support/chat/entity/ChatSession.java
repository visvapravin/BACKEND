package com.forumx.support.chat.entity;

import com.forumx.auth.entity.User;
import com.forumx.common.entity.BaseEntity;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
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
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_chat_sessions_ticket", columnList = "ticket_id"),
        @Index(name = "idx_chat_sessions_tenant", columnList = "tenant_id")
})
public class ChatSession extends BaseEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_chat_sessions_ticket"))
    private Ticket ticket;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_sessions_tenant"))
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_sessions_customer"))
    private User customer;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_sessions_moderator"))
    private User moderator;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatSessionStatus status;
}

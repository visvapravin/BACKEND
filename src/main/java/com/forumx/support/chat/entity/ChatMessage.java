package com.forumx.support.chat.entity;

import com.forumx.auth.entity.User;
import com.forumx.common.entity.BaseEntity;
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
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_session", columnList = "session_id"),
        @Index(name = "idx_chat_messages_sender", columnList = "sender_id"),
        @Index(name = "idx_chat_messages_cursor", columnList = "session_id, created_at, id")
})
public class ChatMessage extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_messages_session"))
    private ChatSession session;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_messages_sender"))
    private User sender;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageType messageType;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageDeliveryStatus deliveryStatus;
}

package com.forumx.support.chat.repository;

import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.MessageDeliveryStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND m.id < :beforeMessageId ORDER BY m.createdAt ASC, m.id ASC")
    Page<ChatMessage> findMessagesBefore(
            @Param("sessionId") Long sessionId,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false ORDER BY m.createdAt ASC, m.id ASC")
    Page<ChatMessage> findMessagesFirstPage(
            @Param("sessionId") Long sessionId,
            Pageable pageable
    );

    List<ChatMessage> findBySession_IdAndSender_IdNotAndDeliveryStatusNotAndDeletedFalse(
            Long sessionId,
            Long senderId,
            MessageDeliveryStatus status
    );
}

package com.forumx.support.chat.repository;

import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.MessageDeliveryStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND EXISTS (SELECT p FROM SupportSessionParticipant p WHERE p.session.id = m.session.id AND p.user.id = :userId AND m.createdAt >= p.joinedAt AND (p.leftAt IS NULL OR m.createdAt <= p.leftAt)) ORDER BY m.createdAt ASC")
    Page<ChatMessage> findAuthorizedMessagesFirstPage(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND m.id < :beforeMessageId AND EXISTS (SELECT p FROM SupportSessionParticipant p WHERE p.session.id = m.session.id AND p.user.id = :userId AND m.createdAt >= p.joinedAt AND (p.leftAt IS NULL OR m.createdAt <= p.leftAt)) ORDER BY m.createdAt ASC")
    Page<ChatMessage> findAuthorizedMessagesBefore(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND m.id < :beforeMessageId")
    Page<ChatMessage> findMessagesBefore(
            @Param("sessionId") Long sessionId,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false")
    Page<ChatMessage> findMessagesFirstPage(
            @Param("sessionId") Long sessionId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false ORDER BY m.createdAt DESC, m.id DESC")
    List<ChatMessage> findLatestMessages(@Param("sessionId") Long sessionId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND (m.createdAt < :cursorCreatedAt OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) ORDER BY m.createdAt DESC, m.id DESC")
    List<ChatMessage> findLatestMessagesBefore(@Param("sessionId") Long sessionId, @Param("cursorCreatedAt") java.time.Instant cursorCreatedAt, @Param("cursorId") Long cursorId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND EXISTS (SELECT p FROM SupportSessionParticipant p WHERE p.session.id = m.session.id AND p.user.id = :userId AND m.createdAt >= p.joinedAt AND (p.leftAt IS NULL OR m.createdAt <= p.leftAt)) ORDER BY m.createdAt DESC, m.id DESC")
    List<ChatMessage> findAuthorizedLatestMessages(@Param("sessionId") Long sessionId, @Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND (m.createdAt < :cursorCreatedAt OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) AND EXISTS (SELECT p FROM SupportSessionParticipant p WHERE p.session.id = m.session.id AND p.user.id = :userId AND m.createdAt >= p.joinedAt AND (p.leftAt IS NULL OR m.createdAt <= p.leftAt)) ORDER BY m.createdAt DESC, m.id DESC")
    List<ChatMessage> findAuthorizedLatestMessagesBefore(@Param("sessionId") Long sessionId, @Param("userId") Long userId, @Param("cursorCreatedAt") java.time.Instant cursorCreatedAt, @Param("cursorId") Long cursorId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND m.createdAt >= :joinedAt")
    Page<ChatMessage> findMessagesForParticipantFirstPage(
            @Param("sessionId") Long sessionId,
            @Param("joinedAt") java.time.Instant joinedAt,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND m.createdAt >= :joinedAt AND m.id < :beforeMessageId")
    Page<ChatMessage> findMessagesForParticipantBefore(
            @Param("sessionId") Long sessionId,
            @Param("joinedAt") java.time.Instant joinedAt,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND m.createdAt >= :joinedAt AND m.createdAt <= :leftAt")
    Page<ChatMessage> findMessagesForParticipantBetweenFirstPage(
            @Param("sessionId") Long sessionId,
            @Param("joinedAt") java.time.Instant joinedAt,
            @Param("leftAt") java.time.Instant leftAt,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sender", "session"})
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.deleted = false AND m.createdAt >= :joinedAt AND m.createdAt <= :leftAt AND m.id < :beforeMessageId")
    Page<ChatMessage> findMessagesForParticipantBetween(
            @Param("sessionId") Long sessionId,
            @Param("joinedAt") java.time.Instant joinedAt,
            @Param("leftAt") java.time.Instant leftAt,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    List<ChatMessage> findBySession_IdAndSender_IdNotAndDeliveryStatusNotAndDeletedFalse(
            Long sessionId,
            Long senderId,
            MessageDeliveryStatus status
    );
}

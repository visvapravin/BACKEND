package com.forumx.question.repository;

import com.forumx.question.entity.QuestionView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface QuestionViewRepository extends JpaRepository<QuestionView, Long> {

    boolean existsByQuestionIdAndViewerId(Long questionId, Long viewerId);

    @Modifying
    @Query(value = """
        INSERT INTO question_views (
            tenant_id, question_id, viewer_id, viewed_at,
            created_at, updated_at, created_by, updated_by, version, deleted
        ) VALUES (
            :tenantId, :questionId, :viewerId, :viewedAt,
            :now, :now, :createdBy, :createdBy, 0, false
        ) ON CONFLICT (question_id, viewer_id) DO NOTHING
    """, nativeQuery = true)
    int insertIfNotExists(
            @Param("tenantId") Long tenantId,
            @Param("questionId") Long questionId,
            @Param("viewerId") Long viewerId,
            @Param("viewedAt") Instant viewedAt,
            @Param("now") Instant now,
            @Param("createdBy") String createdBy
    );
}

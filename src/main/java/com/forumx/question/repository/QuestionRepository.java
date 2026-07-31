package com.forumx.question.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.forumx.question.entity.Question;
import com.forumx.question.entity.QuestionStatus;
import com.forumx.question.dto.response.SearchResultResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link Question} entity operations.
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    /**
     * Finds a question by its ID, tenant ID, and deleted state.
     *
     * @param id       the question ID
     * @param tenantId the tenant ID
     * @return an Optional containing the Question if found, or empty
     */
    Optional<Question> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    /**
     * Finds a question by its ID, tenant ID, status, and deleted state.
     *
     * @param id       the question ID
     * @param tenantId the tenant ID
     * @param status   the status of the question
     * @return an Optional containing the Question if found, or empty
     */
    Optional<Question> findByIdAndTenantIdAndStatusAndDeletedFalse(Long id, Long tenantId, QuestionStatus status);

    /**
     * Checks if a question exists by its ID, tenant ID, and deleted state.
     *
     * @param id       the question ID
     * @param tenantId the tenant ID
     * @return true if exists, false otherwise
     */
    boolean existsByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    /**
     * Finds all questions for a tenant with pagination.
     *
     * @param tenantId the tenant ID
     * @param pageable pagination and sorting parameters
     * @return page of questions
     */
    Page<Question> findAllByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    /**
     * Finds all questions for a tenant with a specific status and pagination.
     *
     * @param tenantId the tenant ID
     * @param status   the status of the questions
     * @param pageable pagination and sorting parameters
     * @return page of questions
     */
    Page<Question> findAllByTenantIdAndStatusAndDeletedFalse(Long tenantId, QuestionStatus status, Pageable pageable);

    /**
     * Finds all questions authored by a user for a tenant with pagination.
     *
     * @param authorId the author's user ID
     * @param tenantId the tenant ID
     * @param pageable pagination and sorting parameters
     * @return page of questions
     */
    Page<Question> findAllByAuthorIdAndTenantIdAndDeletedFalse(Long authorId, Long tenantId, Pageable pageable);



    /**
     * Finds all pinned questions for a tenant sorted by creation date descending.
     *
     * @param tenantId the tenant ID
     * @return list of pinned questions
     */
    List<Question> findAllByTenantIdAndPinnedTrueAndDeletedFalseOrderByCreatedAtDesc(Long tenantId);

    /**
     * Finds all locked questions for a tenant sorted by creation date descending.
     *
     * @param tenantId the tenant ID
     * @return list of locked questions
     */
    List<Question> findAllByTenantIdAndLockedTrueAndDeletedFalseOrderByCreatedAtDesc(Long tenantId);

    /**
     * Counts the total number of questions for a tenant.
     *
     * @param tenantId the tenant ID
     * @return the number of questions
     */
    long countByTenantIdAndDeletedFalse(Long tenantId);

    /**
     * Counts the total number of questions authored by a user for a tenant.
     *
     * @param authorId the author's user ID
     * @param tenantId the tenant ID
     * @return the number of questions
     */
    long countByAuthorIdAndTenantIdAndDeletedFalse(Long authorId, Long tenantId);

    /**
     * Checks if any question exists authored by a user for a tenant.
     *
     * @param authorId the author's user ID
     * @param tenantId the tenant ID
     * @return true if exists, false otherwise
     */
    boolean existsByAuthorIdAndTenantIdAndDeletedFalse(Long authorId, Long tenantId);

    /**
     * Bulk fetches questions by their IDs, tenant ID, and deleted state.
     *
     * @param ids      collection of question IDs
     * @param tenantId the tenant ID
     * @return list of matching questions
     */
    List<Question> findAllByIdInAndTenantIdAndDeletedFalse(Collection<Long> ids, Long tenantId);

    @Modifying
    @Query("UPDATE Question q SET q.viewCount = q.viewCount + 1 WHERE q.id = :questionId")
    int incrementViewCount(@Param("questionId") Long questionId);

    @Query("""
        SELECT new com.forumx.question.dto.response.SearchResultResponse(
            q.id,
            q.title,
            q.content,
            q.author.username,
            q.voteScore,
            q.answerCount,
            q.createdAt
        )
        FROM Question q
        WHERE q.tenant.id = :tenantId
          AND q.deleted = false
          AND q.author.deleted = false
          AND q.author.enabled = true
          AND (
              LOWER(q.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(q.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(q.author.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
    """)
    Page<SearchResultResponse> searchByKeyword(
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

}

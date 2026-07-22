package com.forumx.answer.repository;

import java.util.Optional;

import com.forumx.answer.entity.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link Answer} entity operations.
 */
@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long>, JpaSpecificationExecutor<Answer> {

    /**
     * Finds an answer by its ID, tenant ID, and deleted state.
     *
     * @param id       the answer ID
     * @param tenantId the tenant ID
     * @return an Optional containing the Answer if found, or empty
     */
    Optional<Answer> findByIdAndTenant_IdAndDeletedFalse(Long id, Long tenantId);

    /**
     * Finds all answers for a question and tenant that are not deleted with pagination.
     *
     * @param questionId the question ID
     * @param tenantId   the tenant ID
     * @param pageable   pagination and sorting parameters
     * @return page of answers
     */
    Page<Answer> findByQuestion_IdAndTenant_IdAndDeletedFalse(Long questionId, Long tenantId, Pageable pageable);

    /**
     * Counts the total number of non-deleted answers for a question and tenant.
     *
     * @param questionId the question ID
     * @param tenantId   the tenant ID
     * @return the number of answers
     */
    long countByQuestion_IdAndTenant_IdAndDeletedFalse(Long questionId, Long tenantId);

    /**
     * Checks if an answer exists by its ID, tenant ID, and deleted state.
     *
     * @param id       the answer ID
     * @param tenantId the tenant ID
     * @return true if exists, false otherwise
     */
    boolean existsByIdAndTenant_IdAndDeletedFalse(Long id, Long tenantId);
}

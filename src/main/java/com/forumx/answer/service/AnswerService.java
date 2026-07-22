package com.forumx.answer.service;

import com.forumx.answer.dto.request.CreateAnswerRequest;
import com.forumx.answer.dto.request.UpdateAnswerRequest;
import com.forumx.answer.dto.response.AnswerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface managing business operations for Answers.
 */
public interface AnswerService {

    /**
     * Creates a new Answer under the active tenant context.
     *
     * @param questionId the question identifier
     * @param request    the create request details
     * @return the mapped AnswerResponse details
     */
    AnswerResponse createAnswer(Long questionId, CreateAnswerRequest request);

    /**
     * Retrieves all active answers for a question with pagination.
     *
     * @param questionId the question identifier
     * @param pageable   pagination parameters
     * @return page of AnswerResponse details
     */
    Page<AnswerResponse> getAnswersForQuestion(Long questionId, Pageable pageable);

    /**
     * Retrieves a single active answer by its identifier.
     *
     * @param answerId the answer identifier
     * @return the mapped AnswerResponse details
     */
    AnswerResponse getAnswer(Long answerId);

    /**
     * Updates an existing answer. Only the author or moderators/administrators can update it.
     *
     * @param answerId the answer identifier
     * @param request  the update request details
     * @return the mapped AnswerResponse details
     */
    AnswerResponse updateAnswer(Long answerId, UpdateAnswerRequest request);

    /**
     * Soft-deletes an answer. Only the author or moderators/administrators can delete it.
     *
     * @param answerId the answer identifier
     */
    void deleteAnswer(Long answerId);
}

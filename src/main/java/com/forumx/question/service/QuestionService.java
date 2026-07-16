package com.forumx.question.service;

import com.forumx.question.dto.request.CreateQuestionRequest;
import com.forumx.question.dto.request.UpdateQuestionRequest;
import com.forumx.question.dto.response.QuestionResponse;

/**
 * Service interface managing business operations for Questions.
 */
public interface QuestionService {

    /**
     * Creates a new Question under the active tenant context.
     *
     * @param request the create request details
     * @return the mapped QuestionResponse details
     * @throws jakarta.persistence.EntityNotFoundException if tenant or user are not found
     * @throws org.springframework.security.access.AccessDeniedException    if tenant context or access rules are violated
     * @throws IllegalArgumentException                    if payload validation fails
     */
    QuestionResponse createQuestion(CreateQuestionRequest request);

    /**
     * Retrieves an active, non-archived Question by ID under the active tenant context,
     * incrementing its view counter.
     *
     * @param questionId the question ID
     * @return the mapped QuestionResponse details
     * @throws jakarta.persistence.EntityNotFoundException if the question does not exist or is archived
     * @throws org.springframework.security.access.AccessDeniedException    if tenant context or access rules are violated
     */
    QuestionResponse getQuestion(Long questionId);

    /**
     * Updates an existing, non-archived, non-closed Question by ID under the active tenant context.
     *
     * @param questionId the question ID
     * @param request    the update request details
     * @return the mapped QuestionResponse details
     * @throws jakarta.persistence.EntityNotFoundException if the question is not found or is archived
     * @throws org.springframework.security.access.AccessDeniedException    if user is not authorized or question is closed
     */
    QuestionResponse updateQuestion(Long questionId, UpdateQuestionRequest request);

    /**
     * Soft-deletes a Question by ID under the active tenant context.
     *
     * @param questionId the question ID
     * @throws jakarta.persistence.EntityNotFoundException if the question does not exist or is archived
     * @throws org.springframework.security.access.AccessDeniedException    if user is not authorized or question is closed without elevated permission
     */
    void deleteQuestion(Long questionId);
}

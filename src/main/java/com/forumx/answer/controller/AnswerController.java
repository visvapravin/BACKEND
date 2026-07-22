package com.forumx.answer.controller;

import com.forumx.answer.dto.request.CreateAnswerRequest;
import com.forumx.answer.dto.request.UpdateAnswerRequest;
import com.forumx.answer.dto.response.AnswerResponse;
import com.forumx.answer.service.AnswerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller exposing endpoints for answer operations.
 * Delegates execution flow directly to {@link AnswerService}.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Answers", description = "Operations for managing forum answers.")
public class AnswerController {

    private final AnswerService answerService;

    /**
     * Exposes POST endpoint to create an answer under a question.
     *
     * @param questionId the question ID
     * @param request    the create request DTO
     * @return the ResponseDTO with HTTP status 201
     */
    @PostMapping("/questions/{questionId}/answers")
    @Operation(summary = "Create Answer", description = "Creates a new answer for a question.")
    public ResponseEntity<AnswerResponse> createAnswer(
            @PathVariable Long questionId,
            @Valid @RequestBody CreateAnswerRequest request
    ) {
        AnswerResponse response = answerService.createAnswer(questionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Exposes GET endpoint to fetch all answers for a question with pagination.
     * Defaults to oldest first.
     *
     * @param questionId the question ID
     * @param pageable   pagination and sorting parameter
     * @return the paginated ResponseDTO list with HTTP status 200
     */
    @GetMapping("/questions/{questionId}/answers")
    @Operation(summary = "Get Answers for Question", description = "Retrieves a paginated list of answers for a question.")
    public ResponseEntity<Page<AnswerResponse>> getAnswersForQuestion(
            @PathVariable Long questionId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<AnswerResponse> response = answerService.getAnswersForQuestion(questionId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Exposes GET endpoint to fetch a single answer by its identifier.
     *
     * @param answerId the answer ID
     * @return the ResponseDTO with HTTP status 200
     */
    @GetMapping("/answers/{answerId}")
    @Operation(summary = "Get Answer", description = "Retrieves an answer by its identifier.")
    public ResponseEntity<AnswerResponse> getAnswer(@PathVariable Long answerId) {
        AnswerResponse response = answerService.getAnswer(answerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Exposes PUT endpoint to update an existing answer.
     *
     * @param answerId the answer ID
     * @param request  the update request DTO
     * @return the ResponseDTO with HTTP status 200
     */
    @PutMapping("/answers/{answerId}")
    @Operation(summary = "Update Answer", description = "Updates an existing answer.")
    public ResponseEntity<AnswerResponse> updateAnswer(
            @PathVariable Long answerId,
            @Valid @RequestBody UpdateAnswerRequest request
    ) {
        AnswerResponse response = answerService.updateAnswer(answerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Exposes DELETE endpoint to soft-delete an answer.
     *
     * @param answerId the answer ID
     * @return HTTP status 204
     */
    @DeleteMapping("/answers/{answerId}")
    @Operation(summary = "Delete Answer", description = "Soft deletes an answer.")
    public ResponseEntity<Void> deleteAnswer(@PathVariable Long answerId) {
        answerService.deleteAnswer(answerId);
        return ResponseEntity.noContent().build();
    }
}

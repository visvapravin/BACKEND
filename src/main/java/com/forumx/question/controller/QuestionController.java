package com.forumx.question.controller;

import com.forumx.question.dto.request.CreateQuestionRequest;
import com.forumx.question.dto.request.UpdateQuestionRequest;
import com.forumx.question.dto.response.QuestionResponse;
import com.forumx.question.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * REST Controller exposing endpoints for question operations.
 * Delegates execution flow directly to {@link QuestionService}.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/questions")
@Tag(name = "Questions", description = "Operations for managing forum questions.")
public class QuestionController {

    private final QuestionService questionService;

    /**
     * Exposes POST endpoint to create a question under current tenant context.
     *
     * @param request the create request DTO
     * @return the ResponseDTO with HTTP status 201
     */
    @PostMapping
    @Operation(summary = "Create Question", description = "Creates a new question within the current tenant.")
    public ResponseEntity<QuestionResponse> createQuestion(@Valid @RequestBody CreateQuestionRequest request) {
        QuestionResponse response = questionService.createQuestion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Exposes GET endpoint to fetch a question detail by its identifier.
     *
     * @param questionId the question ID
     * @return the ResponseDTO with HTTP status 200
     */
    @GetMapping("/{questionId}")
    @Operation(summary = "Get Question", description = "Retrieves a question by its identifier.")
    public ResponseEntity<QuestionResponse> getQuestion(@PathVariable Long questionId) {
        QuestionResponse response = questionService.getQuestion(questionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Exposes PUT endpoint to update an existing question.
     *
     * @param questionId the question ID
     * @param request    the update request DTO
     * @return the ResponseDTO with HTTP status 200
     */
    @PutMapping("/{questionId}")
    @Operation(summary = "Update Question", description = "Updates an existing question.")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @PathVariable Long questionId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        QuestionResponse response = questionService.updateQuestion(questionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Exposes DELETE endpoint to soft-delete a question.
     *
     * @param questionId the question ID
     * @return HTTP status 204
     */
    @DeleteMapping("/{questionId}")
    @Operation(summary = "Delete Question", description = "Soft deletes a question.")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }
}

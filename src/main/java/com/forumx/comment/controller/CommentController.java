package com.forumx.comment.controller;

import com.forumx.comment.dto.request.CreateCommentRequest;
import com.forumx.comment.dto.response.CommentResponse;
import com.forumx.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * REST Controller exposing endpoints for Comment operations.
 * Allows users to discuss both Questions and Answers.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Comments", description = "Community Comments API for discussing questions and answers.")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/questions/{questionId}/comments")
    @Operation(summary = "Create Comment for Question", description = "Creates a new comment on the specified question.")
    public ResponseEntity<CommentResponse> createForQuestion(
            @PathVariable Long questionId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        CommentResponse response = commentService.createForQuestion(questionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/answers/{answerId}/comments")
    @Operation(summary = "Create Comment for Answer", description = "Creates a new comment on the specified answer.")
    public ResponseEntity<CommentResponse> createForAnswer(
            @PathVariable Long answerId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        CommentResponse response = commentService.createForAnswer(answerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/questions/{questionId}/comments")
    @Operation(summary = "Get Comments for Question", description = "Retrieves all comments for a question, paginated oldest first.")
    public ResponseEntity<Page<CommentResponse>> getQuestionComments(
            @PathVariable Long questionId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<CommentResponse> response = commentService.getQuestionComments(questionId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/answers/{answerId}/comments")
    @Operation(summary = "Get Comments for Answer", description = "Retrieves all comments for an answer, paginated oldest first.")
    public ResponseEntity<Page<CommentResponse>> getAnswerComments(
            @PathVariable Long answerId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<CommentResponse> response = commentService.getAnswerComments(answerId, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/comments/{commentId}")
    @Operation(summary = "Update Comment", description = "Updates the content of an existing comment. Only author or tenant elevated users are authorized.")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        CommentResponse response = commentService.updateComment(commentId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete Comment", description = "Soft-deletes an existing comment. Only author or tenant elevated users are authorized.")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}

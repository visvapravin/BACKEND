package com.forumx.comment.service;

import com.forumx.comment.dto.request.CreateCommentRequest;
import com.forumx.comment.dto.response.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for Comment module operations.
 */
public interface CommentService {

    CommentResponse createForQuestion(Long questionId, CreateCommentRequest request);

    CommentResponse createForAnswer(Long answerId, CreateCommentRequest request);

    Page<CommentResponse> getQuestionComments(Long questionId, Pageable pageable);

    Page<CommentResponse> getAnswerComments(Long answerId, Pageable pageable);

    CommentResponse updateComment(Long commentId, CreateCommentRequest request);

    void deleteComment(Long commentId);
}

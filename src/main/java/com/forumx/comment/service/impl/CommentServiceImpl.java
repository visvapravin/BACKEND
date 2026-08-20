package com.forumx.comment.service.impl;

import com.forumx.comment.dto.request.CreateCommentRequest;
import com.forumx.comment.dto.response.CommentResponse;
import com.forumx.comment.entity.Comment;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.comment.service.CommentService;
import com.forumx.comment.mapper.CommentMapper;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.answer.entity.Answer;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.resolver.TenantResolver;
import com.forumx.common.exception.QuestionNotFoundException;
import com.forumx.common.exception.AnswerNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final com.forumx.auth.repository.UserRoleRepository userRoleRepository;
    private final CommentMapper commentMapper;
    private final TenantResolver tenantResolver;
    private final AuthenticationFacade authenticationFacade;
    private final NotificationApplicationService notificationApplicationService;

    @Override
    @Transactional
    public CommentResponse createForQuestion(Long questionId, CreateCommentRequest request) {
        CurrentUser current = resolveCurrentUser();

        // 1. Fetch Question and verify tenant alignment
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException("Question not found with ID: " + questionId));
        if (question.isDeleted()) {
            throw new QuestionNotFoundException("Question not found with ID: " + questionId);
        }
        if (!question.getTenant().getId().equals(current.tenantId())) {
            throw new AccessDeniedException("Question belongs to a different tenant");
        }

        // 2. Domain verification
        if (!question.canReceiveComments()) {
            throw new QuestionNotFoundException("Question cannot receive comments");
        }

        // 3. Create Comment
        Comment comment = Comment.builder()
                .question(question)
                .author(current.user())
                .content(request.getContent())
                .build();

        Comment savedComment = commentRepository.save(comment);

        // 4. Send notification if not commenting on own question
        if (!question.getAuthor().getId().equals(current.userId())) {
            try {
                notificationApplicationService.notifyQuestionCommentCreated(
                        current.tenantId(),
                        question.getAuthor().getId(),
                        current.userId(),
                        current.user().getUsername(),
                        question.getId()
                );
            } catch (Exception e) {
                log.error("Failed to send question comment notification", e);
            }
        }

        return commentMapper.toResponse(savedComment);
    }

    @Override
    @Transactional
    public CommentResponse createForAnswer(Long answerId, CreateCommentRequest request) {
        CurrentUser current = resolveCurrentUser();

        // 1. Fetch Answer and verify tenant alignment
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new AnswerNotFoundException("Answer not found with ID: " + answerId));
        if (answer.isDeleted()) {
            throw new AnswerNotFoundException("Answer not found with ID: " + answerId);
        }
        if (!answer.getTenant().getId().equals(current.tenantId())) {
            throw new AccessDeniedException("Answer belongs to a different tenant");
        }

        // 2. Domain verification
        if (!answer.canReceiveComments()) {
            throw new AnswerNotFoundException("Answer cannot receive comments");
        }

        // 3. Create Comment
        Comment comment = Comment.builder()
                .answer(answer)
                .author(current.user())
                .content(request.getContent())
                .build();

        Comment savedComment = commentRepository.save(comment);

        // 4. Send notification if not commenting on own answer
        if (!answer.getAuthor().getId().equals(current.userId())) {
            try {
                notificationApplicationService.notifyAnswerCommentCreated(
                        current.tenantId(),
                        answer.getAuthor().getId(),
                        current.userId(),
                        current.user().getUsername(),
                        answer.getId()
                );
            } catch (Exception e) {
                log.error("Failed to send answer comment notification", e);
            }
        }

        return commentMapper.toResponse(savedComment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getQuestionComments(Long questionId, Pageable pageable) {
        CurrentUser current = resolveCurrentUser();

        // Fetch Question to ensure it exists and belongs to this tenant
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException("Question not found with ID: " + questionId));
        if (question.isDeleted()) {
            throw new QuestionNotFoundException("Question not found with ID: " + questionId);
        }
        if (!question.getTenant().getId().equals(current.tenantId())) {
            throw new AccessDeniedException("Question belongs to a different tenant");
        }

        // Enforce oldest first
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        return commentRepository.findByQuestionAndDeletedFalse(question, sortedPageable)
                .map(commentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getAnswerComments(Long answerId, Pageable pageable) {
        CurrentUser current = resolveCurrentUser();

        // Fetch Answer to ensure it exists and belongs to this tenant
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new AnswerNotFoundException("Answer not found with ID: " + answerId));
        if (answer.isDeleted()) {
            throw new AnswerNotFoundException("Answer not found with ID: " + answerId);
        }
        if (!answer.getTenant().getId().equals(current.tenantId())) {
            throw new AccessDeniedException("Answer belongs to a different tenant");
        }

        // Enforce oldest first
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        return commentRepository.findByAnswerAndDeletedFalse(answer, sortedPageable)
                .map(commentMapper::toResponse);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, CreateCommentRequest request) {
        CurrentUser current = resolveCurrentUser();

        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found with ID: " + commentId));

        // Verify tenant boundary using parent resource tenant
        Long commentTenantId = resolveCommentTenantId(comment);
        if (!commentTenantId.equals(current.tenantId())) {
            throw new AccessDeniedException("Comment belongs to a different tenant");
        }

        // Authorize modification
        boolean isAuthor = comment.getAuthor().getId().equals(current.userId());
        boolean isElevated = elevated(current.details());
        if (!isAuthor && !isElevated) {
            throw new AccessDeniedException("You are not authorized to update this comment");
        }

        // Set edit flags
        comment.setContent(request.getContent());
        comment.setEdited(true);
        comment.setEditedAt(Instant.now());

        Comment updatedComment = commentRepository.save(comment);
        return commentMapper.toResponse(updatedComment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        CurrentUser current = resolveCurrentUser();

        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found with ID: " + commentId));

        // Verify tenant boundary using parent resource tenant
        Long commentTenantId = resolveCommentTenantId(comment);
        if (!commentTenantId.equals(current.tenantId())) {
            throw new AccessDeniedException("Comment belongs to a different tenant");
        }

        // Authorize deletion
        boolean isAuthor = comment.getAuthor().getId().equals(current.userId());
        boolean isElevated = elevated(current.details());
        if (!isAuthor && !isElevated) {
            throw new AccessDeniedException("You are not authorized to delete this comment");
        }

        // Soft delete
        comment.setDeleted(true);
        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
    }

    private Long resolveCommentTenantId(Comment comment) {
        if (comment.getQuestion() != null) {
            return comment.getQuestion().getTenant().getId();
        }
        if (comment.getAnswer() != null) {
            return comment.getAnswer().getTenant().getId();
        }
        throw new com.forumx.common.exception.DomainIntegrityException(
                "Comment is not associated with a tenant-owned resource");
    }

    private CurrentUser resolveCurrentUser() {
        Long tenantId = tenantResolver.resolveTenantId();
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        if (tenantId == null || details == null || details.getTenantId() == null) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        if (!tenantId.equals(details.getTenantId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        User user = userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + details.getUserId()));
        if (!userRoleRepository.existsActiveMembership(user.getId(), tenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        return new CurrentUser(details.getUserId(), tenantId, user, details);
    }

    private boolean elevated(CustomUserDetails details) {
        return details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_TENANT_ADMIN")
                        || authority.equals("ROLE_PLATFORM_ADMIN")
                        || authority.equals("ROLE_MODERATOR")
                        || authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_SUPER_ADMIN"));
    }

    private record CurrentUser(Long userId, Long tenantId, User user, CustomUserDetails details) {
    }
}

package com.forumx.answer.service.impl;

import java.time.Instant;

import com.forumx.answer.dto.request.CreateAnswerRequest;
import com.forumx.answer.dto.request.UpdateAnswerRequest;
import com.forumx.answer.dto.response.AnswerResponse;
import com.forumx.answer.entity.Answer;
import com.forumx.answer.mapper.AnswerMapper;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.answer.service.AnswerService;
import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.UserRepository;
import com.forumx.common.exception.AnswerNotFoundException;
import com.forumx.common.exception.QuestionNotFoundException;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.forumx.notification.service.NotificationApplicationService;

/**
 * Implementation of the {@link AnswerService}.
 * Manages the lifecycle of Answer posts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerServiceImpl implements AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AnswerMapper answerMapper;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;
    private final NotificationApplicationService notificationApplicationService;

    @Override
    @Transactional
    public AnswerResponse createAnswer(Long questionId, CreateAnswerRequest request) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();
        Tenant tenant = validateAndLoadTenant(resolvedTenantId);

        // 2. Resolve User
        CustomUserDetails currentUserDetails = resolveCurrentUser();
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        User author = loadCurrentUser(currentUserDetails);

        // 3. Verify Question Exists & Not Deleted under active tenant context
        Question question = questionRepository.findByIdAndTenantIdAndDeletedFalse(questionId, resolvedTenantId)
                .orElseThrow(() -> new QuestionNotFoundException("Question not found with ID: " + questionId));

        // 4. Create Answer
        Answer answer = answerMapper.toEntity(request);
        answer.setTenant(tenant);
        answer.setQuestion(question);
        answer.setAuthor(author);

        // 5. Save Answer
        Answer savedAnswer = answerRepository.save(answer);

        // 6. Increment Question answerCount
        question.setAnswerCount(question.getAnswerCount() + 1);
        questionRepository.save(question);

        // Future:
        // applicationEventPublisher.publishEvent(
        //     new AnswerCreatedEvent(savedAnswer.getId(), questionId, author.getId(), resolvedTenantId)
        // );

        log.info("Answer created successfully. tenantId={}, userId={}, questionId={}, answerId={}",
                resolvedTenantId, currentUserDetails.getUserId(), questionId, savedAnswer.getId());

        // Create notification for Question creator (unless self-notification)
        if (!question.getAuthor().getId().equals(author.getId())) {
            notificationApplicationService.notifyAnswerCreated(
                    resolvedTenantId,
                    question.getAuthor().getId(),
                    author.getId(),
                    author.getUsername(),
                    questionId
            );
        }

        return answerMapper.toResponse(savedAnswer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AnswerResponse> getAnswersForQuestion(Long questionId, Pageable pageable) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();

        // 2. Resolve User
        CustomUserDetails currentUserDetails = resolveCurrentUser();
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }

        // 3. Verify Question exists & not deleted
        if (!questionRepository.existsByIdAndTenantIdAndDeletedFalse(questionId, resolvedTenantId)) {
            throw new QuestionNotFoundException("Question not found with ID: " + questionId);
        }

        // 4. Fetch Answers
        Page<Answer> answers = answerRepository.findByQuestion_IdAndTenant_IdAndDeletedFalse(questionId, resolvedTenantId, pageable);

        return answers.map(answerMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AnswerResponse getAnswer(Long answerId) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();

        // 2. Resolve User
        CustomUserDetails currentUserDetails = resolveCurrentUser();
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }

        // 3. Fetch Answer
        Answer answer = answerRepository.findByIdAndTenant_IdAndDeletedFalse(answerId, resolvedTenantId)
                .orElseThrow(() -> new AnswerNotFoundException("Answer not found with ID: " + answerId));

        return answerMapper.toResponse(answer);
    }

    @Override
    @Transactional
    public AnswerResponse updateAnswer(Long answerId, UpdateAnswerRequest request) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();

        // 2. Resolve User
        CustomUserDetails currentUserDetails = resolveCurrentUser();
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }

        // 3. Fetch Answer
        Answer answer = answerRepository.findByIdAndTenant_IdAndDeletedFalse(answerId, resolvedTenantId)
                .orElseThrow(() -> new AnswerNotFoundException("Answer not found with ID: " + answerId));

        // 4. Authorization check
        boolean isAuthor = currentUserDetails.getUserId().equals(answer.getAuthor().getId());
        boolean isAuthorized = isAuthor || hasRole(currentUserDetails, RoleType.TENANT_ADMIN) || hasRole(currentUserDetails, RoleType.PLATFORM_ADMIN) || hasRole(currentUserDetails, RoleType.MODERATOR);
        if (!isAuthorized) {
            throw new AccessDeniedException("User is not authorized to update this answer");
        }

        // 5. Update content
        answer.setContent(request.getContent());
        Answer updatedAnswer = answerRepository.save(answer);

        log.info("Answer updated successfully. tenantId={}, userId={}, answerId={}",
                resolvedTenantId, currentUserDetails.getUserId(), answerId);

        return answerMapper.toResponse(updatedAnswer);
    }

    @Override
    @Transactional
    public void deleteAnswer(Long answerId) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();

        // 2. Resolve User
        CustomUserDetails currentUserDetails = resolveCurrentUser();
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }

        // 3. Fetch Answer
        Answer answer = answerRepository.findByIdAndTenant_IdAndDeletedFalse(answerId, resolvedTenantId)
                .orElseThrow(() -> new AnswerNotFoundException("Answer not found with ID: " + answerId));

        // 4. Authorization check
        boolean isAuthor = currentUserDetails.getUserId().equals(answer.getAuthor().getId());
        boolean isAuthorized = isAuthor || hasRole(currentUserDetails, RoleType.TENANT_ADMIN) || hasRole(currentUserDetails, RoleType.PLATFORM_ADMIN) || hasRole(currentUserDetails, RoleType.MODERATOR);
        if (!isAuthorized) {
            throw new AccessDeniedException("User is not authorized to delete this answer");
        }

        // 5. Decrement Question answerCount
        Question question = answer.getQuestion();
        question.setAnswerCount(Math.max(0, question.getAnswerCount() - 1));
        questionRepository.save(question);

        // 6. Soft Delete Answer
        answer.setDeleted(true);
        answer.setDeletedAt(Instant.now());
        answerRepository.save(answer);

        log.info("Answer deleted successfully. tenantId={}, userId={}, answerId={}",
                resolvedTenantId, currentUserDetails.getUserId(), answerId);
    }

    // ── Helper Methods ──────────────────────────────────────────────────

    private Long resolveCurrentTenantId() {
        Long tenantId = tenantResolver.resolveTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context is available");
        }
        return tenantId;
    }

    private CustomUserDetails resolveCurrentUser() {
        CustomUserDetails currentUserDetails = authenticationFacade.getCurrentUserDetails();
        if (currentUserDetails == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        return currentUserDetails;
    }

    private Tenant validateAndLoadTenant(Long tenantId) {
        return tenantRepository.findByIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with ID: " + tenantId));
    }

    private User loadCurrentUser(CustomUserDetails currentUserDetails) {
        User user = userRepository.findByIdAndDeletedFalse(currentUserDetails.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + currentUserDetails.getUserId()));
        if (user.getTenant() == null || !user.getTenant().getId().equals(currentUserDetails.getTenantId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        return user;
    }

    private boolean hasRole(CustomUserDetails userDetails, RoleType roleType) {
        String authority = "ROLE_" + roleType.name();
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals(authority));
    }
}

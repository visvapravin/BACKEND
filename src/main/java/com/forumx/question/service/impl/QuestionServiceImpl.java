package com.forumx.question.service.impl;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.question.dto.request.CreateQuestionRequest;
import com.forumx.question.dto.request.UpdateQuestionRequest;
import com.forumx.question.dto.response.QuestionResponse;
import com.forumx.common.exception.DomainIntegrityException;
import com.forumx.question.event.QuestionCreatedEvent;
import com.forumx.question.service.QuestionService;
import com.forumx.question.entity.Question;
import com.forumx.question.entity.QuestionStatus;
import com.forumx.question.mapper.QuestionMapper;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.question.repository.QuestionViewRepository;
import com.forumx.security.facade.AuthenticationFacade;

import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production-ready implementation of the {@link QuestionService}.
 * Orchestrates business flow for question operations with multi-tenant boundary checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionViewRepository questionViewRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final QuestionMapper questionMapper;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public QuestionResponse createQuestion(CreateQuestionRequest request) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();
        Tenant tenant = validateAndLoadTenant(resolvedTenantId);

        // 2. Resolve User
        CustomUserDetails currentUserDetails = resolveCurrentUser();

        // 3. Validate Tenant Alignment
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }

        User author = loadCurrentUser(currentUserDetails);

        // 4. Map DTO
        Question question = questionMapper.toEntity(request);

        // 5. Populate Entity
        question.setTenant(tenant);
        question.setAuthor(author);
        question.setStatus(QuestionStatus.OPEN);
        question.setViewCount(0);
        question.setAnswerCount(0);
        question.setVoteScore(0);
        question.setPinned(false);
        question.setLocked(false);

        // 6. Save
        Question savedQuestion = questionRepository.save(question);

        // 7. Event Publishing Extension Point
        eventPublisher.publishEvent(QuestionCreatedEvent.builder()
                .questionId(savedQuestion.getId())
                .title(savedQuestion.getTitle())
                .authorId(savedQuestion.getAuthor().getId())
                .tenantId(resolvedTenantId)
                .createdAt(savedQuestion.getCreatedAt())
                .build());

        // 8. Map Response
        log.info("Question created successfully. tenantId={}, userId={}, questionId={}",
                resolvedTenantId, currentUserDetails.getUserId(), savedQuestion.getId());

        return questionMapper.toQuestionResponse(savedQuestion);
    }

    @Override
    @Transactional
    public QuestionResponse getQuestion(Long questionId) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();

        // 2. Resolve Authentication
        CustomUserDetails currentUserDetails = resolveCurrentUser();
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }

        // 3. Load Question
        Question question = questionRepository.findByIdAndTenantIdAndDeletedFalse(questionId, resolvedTenantId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found with ID: " + questionId));

        // 4. Defensive Tenant Check
        if (question.getAuthor() == null || question.getAuthor().getTenant() == null ||
                !question.getAuthor().getTenant().getId().equals(question.getTenant().getId())) {
            throw new DomainIntegrityException("Database corruption detected: Question and Author tenant IDs do not match");
        }

        // 5. Status Validation
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new EntityNotFoundException("Question is archived and not accessible");
        }

        // 6. Unique View Increment (only when newly inserted)
        java.time.Instant now = java.time.Instant.now();
        int inserted = questionViewRepository.insertIfNotExists(
                resolvedTenantId,
                questionId,
                currentUserDetails.getUserId(),
                now,
                now,
                currentUserDetails.getUsername()
        );

        if (inserted > 0) {
            questionRepository.incrementViewCount(questionId);
            question.setViewCount(question.getViewCount() + 1);
        }

        // 7. Logging
        log.info("Question retrieved. tenantId={}, userId={}, questionId={}",
                resolvedTenantId, currentUserDetails.getUserId(), questionId);

        // 8. Map Response
        return questionMapper.toQuestionResponse(question);
    }

    @Override
    @Transactional
    public QuestionResponse updateQuestion(Long questionId, UpdateQuestionRequest request) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();

        // 2. Resolve Current User
        CustomUserDetails currentUserDetails = resolveCurrentUser();
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }

        // 3. Load Question
        Question question = questionRepository.findByIdAndTenantIdAndDeletedFalse(questionId, resolvedTenantId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found with ID: " + questionId));

        // 4. Defensive Tenant Check
        if (question.getAuthor() == null || question.getAuthor().getTenant() == null ||
                !question.getAuthor().getTenant().getId().equals(question.getTenant().getId())) {
            throw new DomainIntegrityException("Database corruption detected: Question and Author tenant IDs do not match");
        }

        // 5. Status Validation
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new EntityNotFoundException("Question is archived and not editable");
        }
        if (question.getStatus() == QuestionStatus.CLOSED) {
            throw new AccessDeniedException("Closed questions cannot be modified");
        }

        // 6. Authorization
        boolean isAuthor = currentUserDetails.getUserId().equals(question.getAuthor().getId());
        boolean isAuthorized = isAuthor || currentUserDetails.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMINISTRATOR") ||
                        auth.equals("ROLE_MODERATOR") ||
                        auth.equals("QUESTION_UPDATE_ANY"));

        if (!isAuthorized) {
            throw new AccessDeniedException("User is not authorized to update this question");
        }

        // 7. Update Editable Fields
        questionMapper.updateQuestionFromRequest(request, question);

        // 8. Logging
        log.info("Question updated successfully. tenantId={}, userId={}, questionId={}",
                resolvedTenantId, currentUserDetails.getUserId(), questionId);

        // 9. Response
        return questionMapper.toQuestionResponse(question);
    }

    @Override
    @Transactional
    public void deleteQuestion(Long questionId) {
        // 1. Resolve Tenant
        Long resolvedTenantId = resolveCurrentTenantId();

        // 2. Resolve Current User
        CustomUserDetails currentUserDetails = resolveCurrentUser();
        if (!currentUserDetails.getTenantId().equals(resolvedTenantId)) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }

        // 3. Load Question
        Question question = questionRepository.findByIdAndTenantIdAndDeletedFalse(questionId, resolvedTenantId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found with ID: " + questionId));

        // 4. Defensive Tenant Check
        if (!question.getTenant().getId().equals(resolvedTenantId) ||
                question.getAuthor() == null || question.getAuthor().getTenant() == null ||
                !question.getAuthor().getTenant().getId().equals(question.getTenant().getId())) {
            throw new DomainIntegrityException("Database corruption detected: Question and Author tenant IDs do not match");
        }

        // 5. Status Validation
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new EntityNotFoundException("Question is archived and not accessible");
        }

        // 6. Authorization
        boolean isAuthor = currentUserDetails.getUserId().equals(question.getAuthor().getId());
        boolean isElevated = currentUserDetails.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_TENANT_ADMIN") ||
                        auth.equals("ROLE_PLATFORM_ADMIN") ||
                        auth.equals("ROLE_ADMIN") ||
                        auth.equals("ROLE_SUPER_ADMIN") ||
                        auth.equals("ROLE_MODERATOR") ||
                        auth.equals("QUESTION_DELETE_ANY"));

        if (question.getStatus() == QuestionStatus.CLOSED) {
            if (!isElevated) {
                throw new AccessDeniedException("Closed questions can only be deleted by moderators or administrators");
            }
        } else {
            // OPEN or ANSWERED
            if (!isAuthor && !isElevated) {
                throw new AccessDeniedException("User is not authorized to delete this question");
            }
        }

        // 7. Soft Delete
        question.setDeleted(true);
        question.setDeletedAt(java.time.Instant.now());

        // 8. Logging
        log.info("Question deleted successfully. tenantId={}, userId={}, questionId={}",
                resolvedTenantId, currentUserDetails.getUserId(), questionId);
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
}

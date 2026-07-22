package com.forumx.bookmark.service.impl;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.bookmark.dto.response.BookmarkResponse;
import com.forumx.bookmark.entity.Bookmark;
import com.forumx.bookmark.mapper.BookmarkMapper;
import com.forumx.bookmark.repository.BookmarkRepository;
import com.forumx.bookmark.service.BookmarkService;
import com.forumx.common.exception.QuestionNotFoundException;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final BookmarkMapper bookmarkMapper;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;

    @Override
    @Transactional
    public BookmarkResponse toggleBookmark(Long questionId) {
        CurrentUser current = resolveCurrentUser();
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException("Question not found with ID: " + questionId));

        if (question.isDeleted()) {
            throw new QuestionNotFoundException("Question not found with ID: " + questionId);
        }

        // Tenant isolation
        if (!question.getTenant().getId().equals(current.tenantId())) {
            throw new AccessDeniedException("Question belongs to a different tenant");
        }

        Optional<Bookmark> existing = bookmarkRepository.findByUserAndQuestion(current.user(), question);
        Bookmark bookmark;

        if (existing.isEmpty()) {
            Tenant tenant = tenantRepository.findByIdAndDeletedFalse(current.tenantId())
                    .orElseThrow(() -> new EntityNotFoundException("Tenant not found with ID: " + current.tenantId()));

            bookmark = Bookmark.builder()
                    .tenant(tenant)
                    .user(current.user())
                    .question(question)
                    .createdBy(String.valueOf(current.userId()))
                    .build();
        } else {
            bookmark = existing.get();
            if (bookmark.isDeleted()) {
                bookmark.restore();
                bookmark.setUpdatedBy(String.valueOf(current.userId()));
            } else {
                bookmark.remove();
                bookmark.setUpdatedBy(String.valueOf(current.userId()));
            }
        }

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        return bookmarkMapper.toResponse(savedBookmark);
    }

    @Override
    @Transactional
    public void removeBookmark(Long questionId) {
        CurrentUser current = resolveCurrentUser();
        Bookmark bookmark = bookmarkRepository.findByUserAndQuestion_IdAndDeletedFalse(current.user(), questionId)
                .orElseThrow(() -> new EntityNotFoundException("Bookmark not found for question ID: " + questionId));

        if (!bookmark.getUser().getId().equals(current.userId())) {
            throw new AccessDeniedException("You are not authorized to remove this bookmark");
        }

        bookmark.remove();
        bookmark.setUpdatedBy(String.valueOf(current.userId()));
        bookmarkRepository.save(bookmark);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookmarkResponse> getMyBookmarks(Pageable pageable) {
        CurrentUser current = resolveCurrentUser();
        Page<Bookmark> bookmarks = bookmarkRepository.findByTenant_IdAndUser_IdAndDeletedFalse(
                current.tenantId(), current.userId(), pageable);
        return bookmarks.map(bookmarkMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBookmarked(Long questionId) {
        CurrentUser current = resolveCurrentUser();
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException("Question not found with ID: " + questionId));

        if (question.isDeleted()) {
            throw new QuestionNotFoundException("Question not found with ID: " + questionId);
        }

        if (!question.getTenant().getId().equals(current.tenantId())) {
            throw new AccessDeniedException("Question belongs to a different tenant");
        }

        return bookmarkRepository.existsByUserAndQuestion_IdAndDeletedFalse(current.user(), questionId);
    }

    // ── Helper Methods ──────────────────────────────────────────────────

    private CurrentUser resolveCurrentUser() {
        Long tenantId = tenantResolver.resolveTenantId();
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        if (tenantId == null || details == null) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        if (!tenantId.equals(details.getTenantId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        User user = userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + details.getUserId()));
        if (user.getTenant() == null || !tenantId.equals(user.getTenant().getId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        return new CurrentUser(details.getUserId(), tenantId, user);
    }

    private record CurrentUser(Long userId, Long tenantId, User user) {
    }
}

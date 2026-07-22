package com.forumx.question.service.impl;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.common.exception.InvalidSearchQueryException;
import com.forumx.question.dto.response.SearchResultResponse;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.question.service.SearchService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;

    @Override
    @Transactional(readOnly = true)
    public Page<SearchResultResponse> searchQuestions(String keyword, Pageable pageable) {
        // Enforce authentication & tenant isolation
        CurrentUser current = resolveCurrentUser();

        // Validate and trim keyword
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new InvalidSearchQueryException("Search query must not be blank");
        }
        String trimmedKeyword = keyword.trim();

        // Validate sorting properties
        validateSort(pageable);

        log.debug("User {} in tenant {} searching questions with keyword: {}", 
                current.userId(), current.tenantId(), trimmedKeyword);

        return questionRepository.searchByKeyword(current.tenantId(), trimmedKeyword, pageable);
    }

    private void validateSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                String property = order.getProperty();
                if (!isAllowedSortProperty(property)) {
                    throw new InvalidSearchQueryException("Sorting by property '" + property + "' is not supported");
                }
            }
        }
    }

    private boolean isAllowedSortProperty(String property) {
        return "createdAt".equals(property)
                || "voteScore".equals(property)
                || "answerCount".equals(property)
                || "title".equals(property);
    }

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
        if (!user.isEnabled()) {
            throw new AccessDeniedException("User is disabled");
        }
        if (user.getTenant() == null || !tenantId.equals(user.getTenant().getId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        return new CurrentUser(details.getUserId(), tenantId, user);
    }

    private record CurrentUser(Long userId, Long tenantId, User user) {
    }
}

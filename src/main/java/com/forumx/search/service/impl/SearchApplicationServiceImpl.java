package com.forumx.search.service.impl;

import com.forumx.search.config.SearchProperties;
import com.forumx.search.domain.AnswerSearchResult;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.AnswerSearchFilter;
import com.forumx.search.dto.request.QuestionSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.request.UserSearchFilter;
import com.forumx.search.dto.response.GlobalSearchResponse;
import com.forumx.search.dto.response.SearchPageResponse;
import com.forumx.search.metrics.SearchMetricsService;
import com.forumx.search.service.SearchApplicationService;
import com.forumx.search.service.SearchService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.tenant.resolver.TenantResolver;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchApplicationServiceImpl implements SearchApplicationService {

    private final SearchService searchService;
    private final TenantResolver tenantResolver;
    private final AuthenticationFacade authenticationFacade;
    private final SearchMetricsService searchMetricsService;
    private final SearchProperties searchProperties;

    @Override
    public SearchPageResponse<QuestionSearchResult> searchQuestions(
            String query, QuestionSearchFilter filter, SearchSort sort, Pageable pageable) {
        Instant start = Instant.now();
        Long tenantId = tenantResolver.resolveTenantId();
        searchMetricsService.recordSearchRequest("QUESTION", searchProperties.getProvider());

        Page<QuestionSearchResult> page = searchService.searchQuestions(tenantId, query, filter, sort, pageable);

        Duration duration = Duration.between(start, Instant.now());
        searchMetricsService.recordSearchDuration("QUESTION", duration);
        if (page.isEmpty()) {
            searchMetricsService.recordEmptyResult("QUESTION");
        }

        return SearchPageResponse.fromPage(page);
    }

    @Override
    public SearchPageResponse<AnswerSearchResult> searchAnswers(
            String query, AnswerSearchFilter filter, SearchSort sort, Pageable pageable) {
        Instant start = Instant.now();
        Long tenantId = tenantResolver.resolveTenantId();
        searchMetricsService.recordSearchRequest("ANSWER", searchProperties.getProvider());

        Page<AnswerSearchResult> page = searchService.searchAnswers(tenantId, query, filter, sort, pageable);

        Duration duration = Duration.between(start, Instant.now());
        searchMetricsService.recordSearchDuration("ANSWER", duration);
        if (page.isEmpty()) {
            searchMetricsService.recordEmptyResult("ANSWER");
        }

        return SearchPageResponse.fromPage(page);
    }

    @Override
    public SearchPageResponse<UserSearchResult> searchUsers(
            String query, UserSearchFilter filter, SearchSort sort, Pageable pageable) {
        Instant start = Instant.now();
        Long tenantId = tenantResolver.resolveTenantId();
        boolean includeEmail = isAdminUser();
        searchMetricsService.recordSearchRequest("USER", searchProperties.getProvider());

        Page<UserSearchResult> page = searchService.searchUsers(tenantId, query, filter, sort, pageable, includeEmail);

        Duration duration = Duration.between(start, Instant.now());
        searchMetricsService.recordSearchDuration("USER", duration);
        if (page.isEmpty()) {
            searchMetricsService.recordEmptyResult("USER");
        }

        return SearchPageResponse.fromPage(page);
    }

    @Override
    public GlobalSearchResponse globalSearch(String query, int limitPerCategory) {
        Instant start = Instant.now();
        Long tenantId = tenantResolver.resolveTenantId();
        boolean includeEmail = isAdminUser();
        searchMetricsService.recordSearchRequest("GLOBAL", searchProperties.getProvider());

        GlobalSearchResponse response = searchService.globalSearch(tenantId, query, limitPerCategory, includeEmail);

        Duration duration = Duration.between(start, Instant.now());
        searchMetricsService.recordSearchDuration("GLOBAL", duration);
        if (response.questions().isEmpty() && response.answers().isEmpty() && response.users().isEmpty()) {
            searchMetricsService.recordEmptyResult("GLOBAL");
        }

        return response;
    }

    private boolean isAdminUser() {
        try {
            var details = authenticationFacade.getCurrentUserDetails();
            if (details != null && details.getAuthorities() != null) {
                return details.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            }
        } catch (Exception ignored) {}
        return false;
    }
}

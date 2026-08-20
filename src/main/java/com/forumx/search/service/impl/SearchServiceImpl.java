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
import com.forumx.search.dto.response.SearchSummary;
import com.forumx.search.provider.AnswerSearchEngine;
import com.forumx.search.provider.QuestionSearchEngine;
import com.forumx.search.provider.UserSearchEngine;
import com.forumx.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("enterpriseSearchService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

    private final QuestionSearchEngine questionSearchEngine;
    private final AnswerSearchEngine answerSearchEngine;
    private final UserSearchEngine userSearchEngine;
    private final SearchProperties searchProperties;

    @Override
    public Page<QuestionSearchResult> searchQuestions(
            Long tenantId, String query, QuestionSearchFilter filter, SearchSort sort, Pageable pageable) {
        return questionSearchEngine.searchQuestions(tenantId, query, filter, sort, pageable);
    }

    @Override
    public Page<AnswerSearchResult> searchAnswers(
            Long tenantId, String query, AnswerSearchFilter filter, SearchSort sort, Pageable pageable) {
        return answerSearchEngine.searchAnswers(tenantId, query, filter, sort, pageable);
    }

    @Override
    public Page<UserSearchResult> searchUsers(
            Long tenantId, String query, UserSearchFilter filter, SearchSort sort, Pageable pageable, boolean includeEmail) {
        return userSearchEngine.searchUsers(tenantId, query, filter, sort, pageable, includeEmail);
    }

    @Override
    public GlobalSearchResponse globalSearch(
            Long tenantId, String query, int limitPerCategory, boolean includeEmail) {
        long startTime = System.currentTimeMillis();
        Pageable limitPage = PageRequest.of(0, limitPerCategory);

        Page<QuestionSearchResult> questionsPage = questionSearchEngine.searchQuestions(
                tenantId, query, null, SearchSort.RELEVANCE, limitPage);
        Page<AnswerSearchResult> answersPage = answerSearchEngine.searchAnswers(
                tenantId, query, null, SearchSort.RELEVANCE, limitPage);
        Page<UserSearchResult> usersPage = userSearchEngine.searchUsers(
                tenantId, query, null, SearchSort.RELEVANCE, limitPage, includeEmail);

        long executionTimeMs = System.currentTimeMillis() - startTime;

        SearchSummary summary = new SearchSummary(
                questionsPage.getTotalElements(),
                answersPage.getTotalElements(),
                usersPage.getTotalElements(),
                searchProperties.getProvider(),
                executionTimeMs
        );

        return new GlobalSearchResponse(
                questionsPage.getContent(),
                answersPage.getContent(),
                usersPage.getContent(),
                summary
        );
    }
}

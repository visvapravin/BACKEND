package com.forumx.search.service;

import com.forumx.search.domain.AnswerSearchResult;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.AnswerSearchFilter;
import com.forumx.search.dto.request.QuestionSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.request.UserSearchFilter;
import com.forumx.search.dto.response.GlobalSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchService {

    Page<QuestionSearchResult> searchQuestions(
            Long tenantId, String query, QuestionSearchFilter filter, SearchSort sort, Pageable pageable);

    Page<AnswerSearchResult> searchAnswers(
            Long tenantId, String query, AnswerSearchFilter filter, SearchSort sort, Pageable pageable);

    Page<UserSearchResult> searchUsers(
            Long tenantId, String query, UserSearchFilter filter, SearchSort sort, Pageable pageable, boolean includeEmail);

    GlobalSearchResponse globalSearch(
            Long tenantId, String query, int limitPerCategory, boolean includeEmail);
}

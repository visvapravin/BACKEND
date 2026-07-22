package com.forumx.search.service;

import com.forumx.search.domain.AnswerSearchResult;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.AnswerSearchFilter;
import com.forumx.search.dto.request.QuestionSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.request.UserSearchFilter;
import com.forumx.search.dto.response.GlobalSearchResponse;
import com.forumx.search.dto.response.SearchPageResponse;
import org.springframework.data.domain.Pageable;

public interface SearchApplicationService {

    SearchPageResponse<QuestionSearchResult> searchQuestions(
            String query, QuestionSearchFilter filter, SearchSort sort, Pageable pageable);

    SearchPageResponse<AnswerSearchResult> searchAnswers(
            String query, AnswerSearchFilter filter, SearchSort sort, Pageable pageable);

    SearchPageResponse<UserSearchResult> searchUsers(
            String query, UserSearchFilter filter, SearchSort sort, Pageable pageable);

    GlobalSearchResponse globalSearch(String query, int limitPerCategory);
}

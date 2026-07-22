package com.forumx.search.provider;

import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.dto.request.QuestionSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QuestionSearchEngine {
    Page<QuestionSearchResult> searchQuestions(
            Long tenantId, String query, QuestionSearchFilter filter, SearchSort sort, Pageable pageable);
}

package com.forumx.search.provider;

import com.forumx.search.domain.AnswerSearchResult;
import com.forumx.search.dto.request.AnswerSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AnswerSearchEngine {
    Page<AnswerSearchResult> searchAnswers(
            Long tenantId, String query, AnswerSearchFilter filter, SearchSort sort, Pageable pageable);
}

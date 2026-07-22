package com.forumx.question.service;

import com.forumx.question.dto.response.SearchResultResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchService {

    /**
     * Searches questions by keyword with pagination and sorting.
     *
     * @param keyword  the search query keyword
     * @param pageable pagination and sorting parameters
     * @return a page of search results
     */
    Page<SearchResultResponse> searchQuestions(String keyword, Pageable pageable);
}

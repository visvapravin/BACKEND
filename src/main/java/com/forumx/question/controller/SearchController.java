package com.forumx.question.controller;

import com.forumx.question.dto.response.SearchResultResponse;
import com.forumx.question.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Search", description = "Operations for searching discussion content.")
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    @Operation(summary = "Search Questions", description = "Search questions by title, content, or author username.")
    public ResponseEntity<Page<SearchResultResponse>> searchQuestions(
            @RequestParam("q") String query,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<SearchResultResponse> response = searchService.searchQuestions(query, pageable);
        return ResponseEntity.ok(response);
    }
}

package com.forumx.search.controller;

import com.forumx.auth.enums.RoleType;
import com.forumx.search.domain.AnswerSearchResult;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.AnswerSearchFilter;
import com.forumx.search.dto.request.QuestionSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.request.UserSearchFilter;
import com.forumx.search.dto.response.GlobalSearchResponse;
import com.forumx.search.dto.response.SearchPageResponse;
import com.forumx.search.service.SearchApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController("enterpriseSearchController")
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
@Tag(name = "Enterprise Search", description = "Operations for searching questions, answers, users, and global multi-entity queries")
public class SearchController {

    private final SearchApplicationService searchApplicationService;

    @GetMapping("/questions")
    @Operation(summary = "Search Questions", description = "Searches questions by title or content with filters, sorting, and pagination")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved question search results")
    public ResponseEntity<SearchPageResponse<QuestionSearchResult>> searchQuestions(
            @Parameter(description = "Search query keyword") @RequestParam(value = "q", required = false) String query,
            @Parameter(description = "Filter by author username or ID") @RequestParam(value = "author", required = false) String author,
            @Parameter(description = "Filter by solved status") @RequestParam(value = "solved", required = false) Boolean solved,
            @Parameter(description = "Filter questions created after date") @RequestParam(value = "createdAfter", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @Parameter(description = "Filter questions created before date") @RequestParam(value = "createdBefore", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @Parameter(description = "Sorting criteria: RELEVANCE, NEWEST, OLDEST, MOST_ANSWERED, MOST_VIEWED, MOST_UPVOTED") @RequestParam(value = "sort", required = false, defaultValue = "RELEVANCE") SearchSort sort,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        QuestionSearchFilter filter = new QuestionSearchFilter(author, solved, createdAfter, createdBefore);
        SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(query, filter, sort, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/answers")
    @Operation(summary = "Search Answers", description = "Searches answers by content with filters, sorting, and pagination")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved answer search results")
    public ResponseEntity<SearchPageResponse<AnswerSearchResult>> searchAnswers(
            @Parameter(description = "Search query keyword") @RequestParam(value = "q", required = false) String query,
            @Parameter(description = "Filter by author username or ID") @RequestParam(value = "author", required = false) String author,
            @Parameter(description = "Filter answers created after date") @RequestParam(value = "createdAfter", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @Parameter(description = "Filter answers created before date") @RequestParam(value = "createdBefore", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @Parameter(description = "Sorting criteria: RELEVANCE, NEWEST, OLDEST") @RequestParam(value = "sort", required = false, defaultValue = "RELEVANCE") SearchSort sort,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        AnswerSearchFilter filter = new AnswerSearchFilter(author, createdAfter, createdBefore);
        SearchPageResponse<AnswerSearchResult> response = searchApplicationService.searchAnswers(query, filter, sort, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    @Operation(summary = "Search Users", description = "Searches users by username or display name with filters, sorting, and pagination (Email masked for non-admins)")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user search results")
    public ResponseEntity<SearchPageResponse<UserSearchResult>> searchUsers(
            @Parameter(description = "Search query keyword") @RequestParam(value = "q", required = false) String query,
            @Parameter(description = "Filter by active status") @RequestParam(value = "active", required = false) Boolean active,
            @Parameter(description = "Filter by user role") @RequestParam(value = "role", required = false) RoleType role,
            @Parameter(description = "Sorting criteria: RELEVANCE, NEWEST, OLDEST") @RequestParam(value = "sort", required = false, defaultValue = "RELEVANCE") SearchSort sort,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UserSearchFilter filter = new UserSearchFilter(active, role);
        SearchPageResponse<UserSearchResult> response = searchApplicationService.searchUsers(query, filter, sort, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/global")
    @Operation(summary = "Global Search", description = "Searches across Questions, Answers, and Users concurrently and returns grouped results with timing summary")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved global search results")
    public ResponseEntity<GlobalSearchResponse> globalSearch(
            @Parameter(description = "Search query keyword") @RequestParam("q") String query,
            @Parameter(description = "Max items per entity category") @RequestParam(value = "limit", required = false, defaultValue = "5") int limit
    ) {
        GlobalSearchResponse response = searchApplicationService.globalSearch(query, limit);
        return ResponseEntity.ok(response);
    }
}

package com.forumx.bookmark.controller;

import com.forumx.bookmark.dto.response.BookmarkResponse;
import com.forumx.bookmark.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Bookmarks", description = "Operations for user private bookmarks on questions.")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/questions/{id}/bookmark")
    @Operation(summary = "Toggle Bookmark", description = "Toggle bookmark status on a question. Adds the bookmark if not bookmarked, or removes it if already bookmarked.")
    public ResponseEntity<BookmarkResponse> toggleBookmark(@PathVariable("id") Long questionId) {
        BookmarkResponse response = bookmarkService.toggleBookmark(questionId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/questions/{id}/bookmark")
    @Operation(summary = "Remove Bookmark", description = "Explicitly remove a bookmark from a question.")
    public ResponseEntity<Void> removeBookmark(@PathVariable("id") Long questionId) {
        bookmarkService.removeBookmark(questionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bookmarks")
    @Operation(summary = "Get My Bookmarks", description = "Retrieve a paginated list of active bookmarks for the current authenticated user.")
    public ResponseEntity<Page<BookmarkResponse>> getMyBookmarks(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BookmarkResponse> response = bookmarkService.getMyBookmarks(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/questions/{id}/bookmark")
    @Operation(summary = "Check Bookmark Status", description = "Check if the current authenticated user has bookmarked the specified question.")
    public ResponseEntity<Boolean> isBookmarked(@PathVariable("id") Long questionId) {
        boolean response = bookmarkService.isBookmarked(questionId);
        return ResponseEntity.ok(response);
    }
}

package com.forumx.bookmark.service;

import com.forumx.bookmark.dto.response.BookmarkResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookmarkService {
    BookmarkResponse toggleBookmark(Long questionId);
    void removeBookmark(Long questionId);
    Page<BookmarkResponse> getMyBookmarks(Pageable pageable);
    boolean isBookmarked(Long questionId);
}

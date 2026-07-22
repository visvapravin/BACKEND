package com.forumx.search.provider;

import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.request.UserSearchFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserSearchEngine {
    Page<UserSearchResult> searchUsers(
            Long tenantId, String query, UserSearchFilter filter, SearchSort sort, Pageable pageable, boolean includeEmail);
}

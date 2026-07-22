package com.forumx.search.dto.request;

import com.forumx.auth.enums.RoleType;

public record UserSearchFilter(
        Boolean active,
        RoleType role
) {
}

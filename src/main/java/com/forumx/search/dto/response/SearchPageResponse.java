package com.forumx.search.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record SearchPageResponse<T>(
        List<T> content,
        int page,
        int size,
        int totalPages,
        long totalElements
) {
    public static <T> SearchPageResponse<T> fromPage(Page<T> springPage) {
        return new SearchPageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalPages(),
                springPage.getTotalElements()
        );
    }
}

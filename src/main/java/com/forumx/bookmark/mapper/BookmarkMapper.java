package com.forumx.bookmark.mapper;

import com.forumx.bookmark.dto.response.BookmarkResponse;
import com.forumx.bookmark.entity.Bookmark;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface BookmarkMapper {

    @Mapping(target = "questionId", source = "question.id")
    @Mapping(target = "questionTitle", source = "question.title")
    BookmarkResponse toResponse(Bookmark bookmark);
}

package com.forumx.comment.mapper;

import com.forumx.comment.dto.response.CommentResponse;
import com.forumx.comment.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for Comment entity to/from DTOs.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface CommentMapper {

    @Mapping(target = "authorId", source = "author.id")
    @Mapping(target = "authorUsername", source = "author.username")
    @Mapping(target = "questionId", source = "question.id")
    @Mapping(target = "answerId", source = "answer.id")
    CommentResponse toResponse(Comment comment);
}

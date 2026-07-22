package com.forumx.answer.mapper;

import com.forumx.answer.dto.request.CreateAnswerRequest;
import com.forumx.answer.dto.response.AnswerResponse;
import com.forumx.answer.entity.Answer;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct Mapper interface for Answers, conversions between entities and API DTOs.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface AnswerMapper {

    /**
     * Maps a {@link CreateAnswerRequest} to a new {@link Answer} entity.
     * Only basic writeable fields are copied; relations are ignored.
     *
     * @param request the create request details
     * @return the mapped Answer entity
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "content", source = "content")
    Answer toEntity(CreateAnswerRequest request);

    /**
     * Maps an {@link Answer} entity to a detailed {@link AnswerResponse} DTO.
     *
     * @param answer the Answer entity
     * @return the mapped response DTO
     */
    @Mapping(target = "authorId", source = "author.id")
    @Mapping(target = "authorUsername", source = "author.username")
    @Mapping(target = "questionId", source = "question.id")
    AnswerResponse toResponse(Answer answer);
}

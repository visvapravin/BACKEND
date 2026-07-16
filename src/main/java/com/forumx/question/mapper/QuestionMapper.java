package com.forumx.question.mapper;

import com.forumx.question.dto.request.CreateQuestionRequest;
import com.forumx.question.dto.request.UpdateQuestionRequest;
import com.forumx.question.dto.response.QuestionResponse;
import com.forumx.question.dto.response.QuestionSummaryResponse;
import com.forumx.question.entity.Question;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct Mapper interface for Questions, conversions between entities and API DTOs.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface QuestionMapper {

    /**
     * Maps a {@link CreateQuestionRequest} to a new {@link Question} entity.
     * Only basic writeable fields (title, content) are copied; relations are ignored.
     *
     * @param request the create request details
     * @return the mapped Question entity
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "content", source = "content")
    Question toEntity(CreateQuestionRequest request);

    /**
     * Updates an existing {@link Question} entity from an {@link UpdateQuestionRequest}.
     * Only basic writeable fields (title, content) are copied; relations are ignored.
     *
     * @param request  the update request details
     * @param question the target Question entity to update
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "content", source = "content")
    void updateQuestionFromRequest(UpdateQuestionRequest request, @MappingTarget Question question);

    /**
     * Maps a {@link Question} entity to a lightweight {@link QuestionSummaryResponse} DTO.
     *
     * @param question the Question entity
     * @return the mapped summary response
     */
    @Mapping(target = "authorId", source = "author.id")
    @Mapping(target = "authorUsername", source = "author.username")
    QuestionSummaryResponse toSummaryResponse(Question question);

    /**
     * Maps a {@link Question} entity to a detailed {@link QuestionResponse} DTO.
     *
     * @param question the Question entity
     * @return the mapped detailed response
     */
    @Mapping(target = "authorId", source = "author.id")
    @Mapping(target = "authorUsername", source = "author.username")
    @Mapping(target = "tenantId", source = "tenant.id")
    QuestionResponse toQuestionResponse(Question question);
}

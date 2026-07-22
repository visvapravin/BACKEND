package com.forumx.vote.mapper;

import com.forumx.vote.dto.response.VoteResponse;
import com.forumx.vote.entity.Vote;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface VoteMapper {
    @Mapping(target = "targetId", ignore = true)
    @Mapping(target = "targetType", ignore = true)
    @Mapping(target = "score", ignore = true)
    VoteResponse toResponse(Vote vote);

    @AfterMapping
    default void setTarget(Vote vote, @MappingTarget VoteResponse response) {
        if (vote.getQuestion() != null) { response.setTargetId(vote.getQuestion().getId()); response.setTargetType("QUESTION"); }
        else if (vote.getAnswer() != null) { response.setTargetId(vote.getAnswer().getId()); response.setTargetType("ANSWER"); }
        else if (vote.getComment() != null) { response.setTargetId(vote.getComment().getId()); response.setTargetType("COMMENT"); }
    }
}

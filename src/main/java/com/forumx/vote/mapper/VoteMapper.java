package com.forumx.vote.mapper;

import com.forumx.vote.dto.response.VoteResponse;
import com.forumx.vote.entity.Vote;
import org.springframework.stereotype.Component;

@Component
public class VoteMapper {
    public VoteResponse toResponse(Vote vote) {
        if (vote == null) return null;
        Long targetId = null;
        String targetType = null;
        if (vote.getQuestion() != null) {
            targetId = vote.getQuestion().getId();
            targetType = "QUESTION";
        } else if (vote.getAnswer() != null) {
            targetId = vote.getAnswer().getId();
            targetType = "ANSWER";
        } else if (vote.getComment() != null) {
            targetId = vote.getComment().getId();
            targetType = "COMMENT";
        }
        return new VoteResponse(targetId, targetType, vote.isDeleted() ? null : vote.getVoteType(), 0L);
    }
}


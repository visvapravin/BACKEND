package com.forumx.vote.dto.response;

import com.forumx.vote.entity.VoteType;

public record ScoreResponse(Long targetId, String targetType, Long score, VoteType userVote) { }


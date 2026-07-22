package com.forumx.vote.dto.request;
import com.forumx.vote.entity.VoteType;
import jakarta.validation.constraints.NotNull;
public record CreateVoteRequest(@NotNull VoteType voteType) { }

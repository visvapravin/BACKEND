package com.forumx.vote.service;
import com.forumx.vote.dto.request.CreateVoteRequest;
import com.forumx.vote.dto.response.ScoreResponse;
import com.forumx.vote.dto.response.VoteResponse;
public interface VoteService {
    VoteResponse voteQuestion(Long questionId, CreateVoteRequest request);
    VoteResponse voteAnswer(Long answerId, CreateVoteRequest request);
    VoteResponse voteComment(Long commentId, CreateVoteRequest request);
    ScoreResponse getQuestionScore(Long questionId);
    ScoreResponse getAnswerScore(Long answerId);
    ScoreResponse getCommentScore(Long commentId);
}

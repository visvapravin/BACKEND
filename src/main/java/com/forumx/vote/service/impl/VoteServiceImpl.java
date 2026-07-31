package com.forumx.vote.service.impl;

import java.time.Instant;
import java.util.Optional;
import com.forumx.answer.entity.Answer;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.comment.entity.Comment;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.resolver.TenantResolver;
import com.forumx.vote.dto.request.CreateVoteRequest;
import com.forumx.vote.dto.response.ScoreResponse;
import com.forumx.vote.dto.response.VoteResponse;
import com.forumx.vote.entity.Vote;
import com.forumx.vote.entity.VoteType;
import com.forumx.vote.mapper.VoteMapper;
import com.forumx.vote.repository.VoteRepository;
import com.forumx.vote.service.VoteService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor
public class VoteServiceImpl implements VoteService {
    private final VoteRepository voteRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final VoteMapper voteMapper;
    private final TenantResolver tenantResolver;
    private final AuthenticationFacade authenticationFacade;
    private final NotificationApplicationService notificationApplicationService;

    @Override @Transactional public VoteResponse voteQuestion(Long id, CreateVoteRequest request) {
        CurrentUser current = current(); Question target = question(id, current.tenantId());
        if (!target.canReceiveVotes()) throw new AccessDeniedException("Question cannot receive votes");
        rejectSelf(target.getAuthor(), current); Vote vote = apply(voteRepository.findByQuestionAndVoter(target, current.user()), request.voteType(), current.user(), v -> v.setQuestion(target));
        long score = questionScore(target); notifyQuestion(target, current, vote); return response(vote, score);
    }
    @Override @Transactional public VoteResponse voteAnswer(Long id, CreateVoteRequest request) {
        CurrentUser current = current(); Answer target = answer(id, current.tenantId());
        if (!target.canReceiveVotes()) throw new AccessDeniedException("Answer cannot receive votes");
        rejectSelf(target.getAuthor(), current); Vote vote = apply(voteRepository.findByAnswerAndVoter(target, current.user()), request.voteType(), current.user(), v -> v.setAnswer(target));
        long score = answerScore(target); notifyAnswer(target, current, vote); return response(vote, score);
    }
    @Override @Transactional public VoteResponse voteComment(Long id, CreateVoteRequest request) {
        CurrentUser current = current(); Comment target = comment(id, current.tenantId());
        if (!target.canReceiveVotes()) throw new AccessDeniedException("Comment cannot receive votes");
        rejectSelf(target.getAuthor(), current); Vote vote = apply(voteRepository.findByCommentAndVoter(target, current.user()), request.voteType(), current.user(), v -> v.setComment(target));
        long score = commentScore(target); notifyComment(target, current, vote); return response(vote, score);
    }
    @Override @Transactional(readOnly = true) public ScoreResponse getQuestionScore(Long id) { CurrentUser c=current(); Question q=question(id,c.tenantId()); VoteType userVote = voteRepository.findByQuestionAndVoterAndDeletedFalse(q, c.user()).map(Vote::getVoteType).orElse(null); return new ScoreResponse(id,"QUESTION",questionScore(q),userVote); }
    @Override @Transactional(readOnly = true) public ScoreResponse getAnswerScore(Long id) { CurrentUser c=current(); Answer a=answer(id,c.tenantId()); VoteType userVote = voteRepository.findByAnswerAndVoterAndDeletedFalse(a, c.user()).map(Vote::getVoteType).orElse(null); return new ScoreResponse(id,"ANSWER",answerScore(a),userVote); }
    @Override @Transactional(readOnly = true) public ScoreResponse getCommentScore(Long id) { CurrentUser c=current(); Comment m=comment(id,c.tenantId()); VoteType userVote = voteRepository.findByCommentAndVoterAndDeletedFalse(m, c.user()).map(Vote::getVoteType).orElse(null); return new ScoreResponse(id,"COMMENT",commentScore(m),userVote); }

    private Vote apply(Optional<Vote> existing, VoteType requested, User voter, java.util.function.Consumer<Vote> target) {
        Vote vote = existing.orElseGet(() -> { Vote created=Vote.builder().voter(voter).voteType(requested).build(); target.accept(created); return created; });
        if (existing.isPresent() && !vote.isDeleted() && vote.getVoteType() == requested) { vote.setDeleted(true); vote.setDeletedAt(Instant.now()); }
        else { vote.setDeleted(false); vote.setDeletedAt(null); vote.setVoteType(requested); }
        return voteRepository.save(vote);
    }
    private VoteResponse response(Vote vote, long score) { VoteResponse r=voteMapper.toResponse(vote); r.setScore(score); return r; }
    private long questionScore(Question q) { return voteRepository.countByQuestionAndVoteTypeAndDeletedFalse(q, VoteType.UPVOTE)-voteRepository.countByQuestionAndVoteTypeAndDeletedFalse(q, VoteType.DOWNVOTE); }
    private long answerScore(Answer a) { return voteRepository.countByAnswerAndVoteTypeAndDeletedFalse(a, VoteType.UPVOTE)-voteRepository.countByAnswerAndVoteTypeAndDeletedFalse(a, VoteType.DOWNVOTE); }
    private long commentScore(Comment c) { return voteRepository.countByCommentAndVoteTypeAndDeletedFalse(c, VoteType.UPVOTE)-voteRepository.countByCommentAndVoteTypeAndDeletedFalse(c, VoteType.DOWNVOTE); }
    private void rejectSelf(User author, CurrentUser current) { if (author.getId().equals(current.userId())) throw new AccessDeniedException("Users cannot vote on their own content"); }
    private Question question(Long id, Long tenant) { Question q=questionRepository.findById(id).orElseThrow(()->new EntityNotFoundException("Question not found")); if (!q.getTenant().getId().equals(tenant)) throw new AccessDeniedException("Cross-tenant access denied"); return q; }
    private Answer answer(Long id, Long tenant) { Answer a=answerRepository.findById(id).orElseThrow(()->new EntityNotFoundException("Answer not found")); if (!a.getTenant().getId().equals(tenant)) throw new AccessDeniedException("Cross-tenant access denied"); return a; }
    private Comment comment(Long id, Long tenant) { Comment m=commentRepository.findById(id).orElseThrow(()->new EntityNotFoundException("Comment not found")); if (!m.getAuthor().getTenant().getId().equals(tenant)) throw new AccessDeniedException("Cross-tenant access denied"); return m; }
    private CurrentUser current() { Long t=tenantResolver.resolveTenantId(); CustomUserDetails d=authenticationFacade.getCurrentUserDetails(); if(t==null||d==null||!t.equals(d.getTenantId())) throw new AccessDeniedException("Authenticated tenant context is required"); User u=userRepository.findByIdAndDeletedFalse(d.getUserId()).orElseThrow(()->new EntityNotFoundException("User not found")); return new CurrentUser(u.getId(),t,u); }
    private void notifyQuestion(Question q, CurrentUser c, Vote v) { if(!v.isDeleted()&&v.getVoteType()==VoteType.UPVOTE) try { notificationApplicationService.notifyQuestionUpvoted(c.tenantId(),q.getAuthor().getId(),c.userId(),c.user().getUsername(),q.getId()); } catch(Exception e) { log.error("Vote notification failed",e); } }
    private void notifyAnswer(Answer a, CurrentUser c, Vote v) { if(!v.isDeleted()&&v.getVoteType()==VoteType.UPVOTE) try { notificationApplicationService.notifyAnswerUpvoted(c.tenantId(),a.getAuthor().getId(),c.userId(),c.user().getUsername(),a.getId()); } catch(Exception e) { log.error("Vote notification failed",e); } }
    private void notifyComment(Comment m, CurrentUser c, Vote v) { if(!v.isDeleted()&&v.getVoteType()==VoteType.UPVOTE) try { notificationApplicationService.notifyCommentUpvoted(c.tenantId(),m.getAuthor().getId(),c.userId(),c.user().getUsername(),m.getId()); } catch(Exception e) { log.error("Vote notification failed",e); } }
    private record CurrentUser(Long userId, Long tenantId, User user) { }
}

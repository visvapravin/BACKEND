package com.forumx.vote.repository;

import java.util.Optional;
import com.forumx.answer.entity.Answer;
import com.forumx.auth.entity.User;
import com.forumx.comment.entity.Comment;
import com.forumx.question.entity.Question;
import com.forumx.vote.entity.Vote;
import com.forumx.vote.entity.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByQuestionAndVoterAndDeletedFalse(Question question, User voter);
    Optional<Vote> findByAnswerAndVoterAndDeletedFalse(Answer answer, User voter);
    Optional<Vote> findByCommentAndVoterAndDeletedFalse(Comment comment, User voter);
    Optional<Vote> findByQuestionAndVoter(Question question, User voter);
    Optional<Vote> findByAnswerAndVoter(Answer answer, User voter);
    Optional<Vote> findByCommentAndVoter(Comment comment, User voter);
    long countByQuestionAndVoteTypeAndDeletedFalse(Question question, VoteType type);
    long countByAnswerAndVoteTypeAndDeletedFalse(Answer answer, VoteType type);
    long countByCommentAndVoteTypeAndDeletedFalse(Comment comment, VoteType type);
}

package com.forumx.vote.entity;

import com.forumx.answer.entity.Answer;
import com.forumx.auth.entity.User;
import com.forumx.comment.entity.Comment;
import com.forumx.common.entity.BaseEntity;
import com.forumx.question.entity.Question;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Getter @Setter @SuperBuilder @NoArgsConstructor @AllArgsConstructor
@DynamicInsert @DynamicUpdate @Entity @Table(name = "votes")
public class Vote extends BaseEntity {
    @NotNull @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "voter_id", nullable = false)
    private User voter;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "question_id") private Question question;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "answer_id") private Answer answer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "comment_id") private Comment comment;
    @NotNull @Enumerated(EnumType.STRING) private VoteType voteType;
}

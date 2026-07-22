package com.forumx.comment.entity;

import com.forumx.auth.entity.User;
import com.forumx.common.entity.BaseEntity;
import com.forumx.question.entity.Question;
import com.forumx.answer.entity.Answer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import java.time.Instant;

/**
 * JPA entity representing a comment on a Question or an Answer.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@Entity
@Table(
        name = "comments",
        indexes = {
                @Index(name = "idx_comments_question_id", columnList = "question_id"),
                @Index(name = "idx_comments_answer_id", columnList = "answer_id"),
                @Index(name = "idx_comments_author_id", columnList = "author_id"),
                @Index(name = "idx_comments_parent_comment_id", columnList = "parent_comment_id"),
                @Index(name = "idx_comments_created_at", columnList = "created_at")
        }
)
public class Comment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id")
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @NotBlank
    @Size(min = 2, max = 2000)
    @Column(nullable = false, length = 2000)
    private String content;

    @Builder.Default
    @Column(nullable = false)
    private boolean edited = false;

    @Column(name = "edited_at")
    private Instant editedAt;

    public boolean canReceiveVotes() { return !isDeleted(); }

    public boolean canBeReported() {
        return !isDeleted();
    }
}

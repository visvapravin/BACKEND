package com.forumx.answer.entity;

import com.forumx.auth.entity.User;
import com.forumx.common.entity.BaseEntity;
import com.forumx.question.entity.Question;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Entity representing an answer to a question.
 * Answers are tenant-scoped and authored by a user.
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
        name = "answers",
        indexes = {
                @Index(name = "idx_answers_tenant", columnList = "tenant_id"),
                @Index(name = "idx_answers_question", columnList = "question_id"),
                @Index(name = "idx_answers_author", columnList = "author_id"),
                @Index(name = "idx_answers_created_at", columnList = "created_at"),
                @Index(name = "idx_answers_deleted", columnList = "deleted")
        }
)
public class Answer extends BaseEntity {

    /**
     * The tenant associated with the answer.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_answers_tenant"))
    private Tenant tenant;

    /**
     * The question this answer responds to.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, foreignKey = @ForeignKey(name = "fk_answers_question"))
    private Question question;

    /**
     * The author who wrote the answer.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_answers_author"))
    private User author;

    /**
     * The textual content of the answer.
     */
    @NotBlank
    @Size(min = 10, max = 10000)
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public boolean canReceiveComments() {
        return !isDeleted();
    }

    public boolean canReceiveVotes() { return !isDeleted(); }

    public boolean canBeReported() {
        return !isDeleted();
    }
}

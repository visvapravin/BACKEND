package com.forumx.question.entity;

import com.forumx.auth.entity.User;
import com.forumx.common.entity.BaseEntity;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import com.forumx.answer.entity.Answer;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a question post.
 * Questions are tenant-scoped and authored by a user.
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
        name = "questions",
        indexes = {
                @Index(name = "idx_questions_tenant", columnList = "tenant_id"),
                @Index(name = "idx_questions_author", columnList = "author_id"),
                @Index(name = "idx_questions_status", columnList = "status"),
                @Index(name = "idx_questions_deleted", columnList = "deleted"),
                @Index(name = "idx_questions_tenant_status", columnList = "tenant_id, status")
        }
)
public class Question extends BaseEntity {

    /**
     * The tenant associated with the question.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_questions_tenant"))
    private Tenant tenant;

    /**
     * The author (user) who created the question.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_questions_author"))
    private User author;



    /**
     * The title of the question.
     */
    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * The main text content of the question.
     */
    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * The operational state status of the question.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private QuestionStatus status = QuestionStatus.OPEN;

    /**
     * Counter for view counts.
     */
    @NotNull
    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    /**
     * Counter for answers submitted.
     */
    @NotNull
    @Builder.Default
    @Column(name = "answer_count", nullable = false)
    private Integer answerCount = 0;

    /**
     * Running total score of upvotes/downvotes.
     */
    @NotNull
    @Builder.Default
    @Column(name = "vote_score", nullable = false)
    private Integer voteScore = 0;

    /**
     * True if the question is pinned at the top.
     */
    @NotNull
    @Builder.Default
    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    /**
     * True if the question is locked (disallowing further responses).
     */
    @NotNull
    @Builder.Default
    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    /**
     * The list of answers associated with this question.
     */
    @OneToMany(
            mappedBy = "question",
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Answer> answers = new ArrayList<>();

    public boolean canReceiveComments() {
        return !isDeleted();
    }

    public boolean canReceiveVotes() {
        return !isDeleted() && status != QuestionStatus.CLOSED;
    }

    public boolean canBeReported() {
        return !isDeleted();
    }
}

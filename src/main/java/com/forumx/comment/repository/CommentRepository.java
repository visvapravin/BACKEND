package com.forumx.comment.repository;

import com.forumx.comment.entity.Comment;
import com.forumx.question.entity.Question;
import com.forumx.answer.entity.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Spring Data JPA Repository for Comment entity.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByQuestionAndDeletedFalse(Question question, Pageable pageable);

    Page<Comment> findByAnswerAndDeletedFalse(Answer answer, Pageable pageable);

    Optional<Comment> findByIdAndDeletedFalse(Long id);
}

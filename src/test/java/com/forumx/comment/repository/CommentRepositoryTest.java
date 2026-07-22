package com.forumx.comment.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.comment.entity.Comment;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.answer.entity.Answer;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant tenant;
    private User author;
    private Question question;
    private Answer answer;

    @BeforeEach
    public void setUp() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("Comment Repo Tenant")
                .slug("comment-repo-tenant")
                .build());

        author = userRepository.save(User.builder()
                .username("commenter_repo")
                .email("commenter_repo@test.com")
                .tenant(tenant)
                .enabled(true)
                .build());

        question = questionRepository.save(Question.builder()
                .title("Repo Question")
                .content("Question details")
                .author(author)
                .tenant(tenant)
                .build());

        answer = answerRepository.save(Answer.builder()
                .question(question)
                .author(author)
                .content("Here is a very long and detailed answer content to pass validation constraint.")
                .tenant(tenant)
                .build());
    }

    @Test
    public void testSaveAndFindQuestionComments() {
        Comment comment1 = commentRepository.save(Comment.builder()
                .question(question)
                .author(author)
                .content("Comment 1 content")
                .build());

        Comment comment2 = commentRepository.save(Comment.builder()
                .question(question)
                .author(author)
                .content("Comment 2 content")
                .build());

        Page<Comment> page = commentRepository.findByQuestionAndDeletedFalse(question, PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().contains(comment1));
        assertTrue(page.getContent().contains(comment2));
    }

    @Test
    public void testFindQuestionCommentsExcludesDeleted() {
        commentRepository.save(Comment.builder()
                .question(question)
                .author(author)
                .content("Active comment")
                .build());

        commentRepository.save(Comment.builder()
                .question(question)
                .author(author)
                .content("Deleted comment")
                .deleted(true)
                .build());

        Page<Comment> page = commentRepository.findByQuestionAndDeletedFalse(question, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("Active comment", page.getContent().get(0).getContent());
    }

    @Test
    public void testSaveAndFindAnswerComments() {
        Comment comment = commentRepository.save(Comment.builder()
                .answer(answer)
                .author(author)
                .content("Comment on answer")
                .build());

        Page<Comment> page = commentRepository.findByAnswerAndDeletedFalse(answer, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals(comment.getId(), page.getContent().get(0).getId());
    }

    @Test
    public void testFindByIdAndDeletedFalse() {
        Comment comment = commentRepository.save(Comment.builder()
                .question(question)
                .author(author)
                .content("Another comment")
                .build());

        Optional<Comment> found = commentRepository.findByIdAndDeletedFalse(comment.getId());
        assertTrue(found.isPresent());

        comment.setDeleted(true);
        commentRepository.save(comment);

        Optional<Comment> notFound = commentRepository.findByIdAndDeletedFalse(comment.getId());
        assertFalse(notFound.isPresent());
    }
}

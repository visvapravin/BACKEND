package com.forumx.question.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.question.dto.response.SearchResultResponse;
import com.forumx.question.entity.Question;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.forumx.ForumXApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ForumXApplication.class)
@ActiveProfiles("dev")
@Transactional
public class SearchRepositoryTest {

    @Autowired private QuestionRepository questionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private com.forumx.bookmark.repository.BookmarkRepository bookmarkRepository;
    @Autowired private com.forumx.comment.repository.CommentRepository commentRepository;
    @Autowired private com.forumx.support.ticket.repository.TicketRepository ticketRepository;
    @Autowired private com.forumx.support.ticket.repository.TicketMessageRepository ticketMessageRepository;
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private com.forumx.answer.repository.AnswerRepository answerRepository;
    @Autowired private com.forumx.moderation.repository.ModerationReportRepository reportRepository;
    @Autowired private com.forumx.auth.repository.UserRoleRepository userRoleRepository;
    @Autowired private com.forumx.auth.repository.UserProfileRepository userProfileRepository;
    @Autowired private com.forumx.auth.repository.RefreshTokenRepository refreshTokenRepository;
    @Autowired private com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private com.forumx.auth.verification.repository.VerificationTokenRepository verificationTokenRepository;

    private Tenant tenant1;
    private Tenant tenant2;
    private User author1;
    private User author2;
    private User authorDisabled;
    private User authorDeleted;

    @BeforeEach
    public void setUp() {
        ticketMessageRepository.deleteAllInBatch();
        reportRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        bookmarkRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        answerRepository.deleteAllInBatch();
        questionRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        verificationTokenRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRoleRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        tenant1 = tenantRepository.save(Tenant.builder()
                .name("Search Repo Tenant 1")
                .slug("search-t1-repo-test")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        tenant2 = tenantRepository.save(Tenant.builder()
                .name("Search Repo Tenant 2")
                .slug("search-t2-repo-test")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        author1 = userRepository.save(User.builder()
                .username("search_author1")
                .email("sauthor1@test.com")
                .tenant(tenant1)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        author2 = userRepository.save(User.builder()
                .username("search_author2")
                .email("sauthor2@test.com")
                .tenant(tenant2)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());

        authorDisabled = userRepository.save(User.builder()
                .username("disabled_author")
                .email("disabled@test.com")
                .tenant(tenant1)
                .enabled(false)
                .status(User.UserStatus.ACTIVE)
                .build());

        authorDeleted = userRepository.save(User.builder()
                .username("deleted_author")
                .email("deleted@test.com")
                .tenant(tenant1)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        authorDeleted.setDeleted(true);
        authorDeleted = userRepository.save(authorDeleted);
    }

    @Test
    public void testSearchByKeyword_BasicMatching() {
        // Given
        questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("Testing Spring Boot applications")
                .content("This is a guide on testing Spring Boot apps.")
                .build());

        questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("Java Programming basics")
                .content("Learning Java is fun and rewarding.")
                .build());

        // When (search for 'spring')
        Page<SearchResultResponse> result = questionRepository.searchByKeyword(
                tenant1.getId(), "spring", PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals("Testing Spring Boot applications", result.getContent().getFirst().getTitle());
        assertEquals("search_author1", result.getContent().getFirst().getAuthorName());
    }

    @Test
    public void testSearchByKeyword_CaseInsensitivePartialMatching() {
        // Given
        questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("Learn PostgreSQL Trigram Search")
                .content("Trigrams are amazing for fuzzy searches.")
                .build());

        // When
        Page<SearchResultResponse> result = questionRepository.searchByKeyword(
                tenant1.getId(), "pOsTgRe", PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().getFirst().getTitle().contains("PostgreSQL"));
    }

    @Test
    public void testSearchByKeyword_AuthorUsernameMatching() {
        // Given
        questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("A random topic")
                .content("Some content.")
                .build());

        // When (search for author username)
        Page<SearchResultResponse> result = questionRepository.searchByKeyword(
                tenant1.getId(), "search_author1", PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals("A random topic", result.getContent().getFirst().getTitle());
    }

    @Test
    public void testSearchByKeyword_TenantIsolation() {
        // Given
        questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("Spring boot on tenant 1")
                .content("Tenant 1 content.")
                .build());

        questionRepository.save(Question.builder()
                .tenant(tenant2)
                .author(author2)
                .title("Spring boot on tenant 2")
                .content("Tenant 2 content.")
                .build());

        // When
        Page<SearchResultResponse> resultT1 = questionRepository.searchByKeyword(
                tenant1.getId(), "spring", PageRequest.of(0, 10));
        Page<SearchResultResponse> resultT2 = questionRepository.searchByKeyword(
                tenant2.getId(), "spring", PageRequest.of(0, 10));

        // Then
        assertEquals(1, resultT1.getTotalElements());
        assertEquals("Spring boot on tenant 1", resultT1.getContent().getFirst().getTitle());

        assertEquals(1, resultT2.getTotalElements());
        assertEquals("Spring boot on tenant 2", resultT2.getContent().getFirst().getTitle());
    }

    @Test
    public void testSearchByKeyword_Exclusions() {
        // Given
        // 1. Deleted question
        Question deletedQ = Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("Deleted Spring post")
                .content("I will be deleted.")
                .build();
        deletedQ.setDeleted(true);
        questionRepository.save(deletedQ);

        // 2. Disabled author
        questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(authorDisabled)
                .title("Spring post from disabled author")
                .content("My author is disabled.")
                .build());

        // 3. Deleted author
        questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(authorDeleted)
                .title("Spring post from deleted author")
                .content("My author is deleted.")
                .build());

        // 4. Valid question
        questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("Active Spring post")
                .content("All good.")
                .build());

        // When
        Page<SearchResultResponse> result = questionRepository.searchByKeyword(
                tenant1.getId(), "spring", PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals("Active Spring post", result.getContent().getFirst().getTitle());
    }

    @Test
    public void testSearchByKeyword_PaginationAndSorting() {
        // Given
        Question q1 = questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("Spring post A")
                .content("Content A")
                .build());
        q1.setVoteScore(10);
        q1 = questionRepository.save(q1);

        Question q2 = questionRepository.save(Question.builder()
                .tenant(tenant1)
                .author(author1)
                .title("Spring post B")
                .content("Content B")
                .build());
        q2.setVoteScore(20);
        q2 = questionRepository.save(q2);

        // When: Sort by voteScore DESC
        Page<SearchResultResponse> resultDesc = questionRepository.searchByKeyword(
                tenant1.getId(), "spring", PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "voteScore")));

        // Then
        assertEquals(2, resultDesc.getTotalElements());
        assertEquals(1, resultDesc.getContent().size());
        assertEquals("Spring post B", resultDesc.getContent().getFirst().getTitle());

        // When: Sort by voteScore ASC
        Page<SearchResultResponse> resultAsc = questionRepository.searchByKeyword(
                tenant1.getId(), "spring", PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "voteScore")));

        // Then
        assertEquals("Spring post A", resultAsc.getContent().getFirst().getTitle());
    }
}

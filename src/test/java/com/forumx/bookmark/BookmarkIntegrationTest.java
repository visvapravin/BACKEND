package com.forumx.bookmark;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.verification.repository.VerificationTokenRepository;
import com.forumx.bookmark.dto.response.BookmarkResponse;
import com.forumx.bookmark.entity.Bookmark;
import com.forumx.bookmark.repository.BookmarkRepository;
import com.forumx.bookmark.service.BookmarkService;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.common.exception.QuestionNotFoundException;
import com.forumx.moderation.repository.ModerationReportRepository;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.repository.TicketMessageRepository;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class)
public class BookmarkIntegrationTest {

    @Autowired private BookmarkService bookmarkService;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private ModerationReportRepository reportRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketMessageRepository ticketMessageRepository;
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private com.forumx.answer.repository.AnswerRepository answerRepository;

    @MockBean private AuthenticationFacade authenticationFacade;
    @MockBean private TenantResolver tenantResolver;

    private Tenant tenant1;
    private Tenant tenant2;

    private User t1User;
    private User t2User;

    private Question t1Question;

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

        // 1. Setup Tenants
        tenant1 = tenantRepository.findAll().stream()
                .filter(t -> t.getSlug().equals("default"))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("Tenant 1").slug("default").build()));

        tenant2 = tenantRepository.findAll().stream()
                .filter(t -> t.getSlug().equals("tenant2"))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("Tenant 2").slug("tenant2").build()));

        // 2. Setup Roles
        Role userRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.USER).active(true).build()));

        // 3. Setup Users
        t1User = userRepository.save(User.builder().username("t1_usr").email("t1_usr@test.com").tenant(tenant1).enabled(true).build());
        userRoleRepository.save(UserRole.builder().user(t1User).role(userRole).active(true).build());

        t2User = userRepository.save(User.builder().username("t2_usr").email("t2_usr@test.com").tenant(tenant2).enabled(true).build());
        userRoleRepository.save(UserRole.builder().user(t2User).role(userRole).active(true).build());

        t1User = userRepository.findByIdWithFullProfile(t1User.getId()).orElseThrow();
        t2User = userRepository.findByIdWithFullProfile(t2User.getId()).orElseThrow();

        // 4. Setup Content
        t1Question = questionRepository.save(Question.builder()
                .title("T1 Question")
                .content("Content")
                .author(t1User)
                .tenant(tenant1)
                .build());
    }

    private void authenticateUser(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);
        when(tenantResolver.resolveTenantId()).thenReturn(user.getTenant().getId());
    }

    @Test
    public void testToggleBookmarkFlow() {
        authenticateUser(t1User);

        // Bookmark first time (adds bookmark)
        BookmarkResponse response = bookmarkService.toggleBookmark(t1Question.getId());
        assertNotNull(response);
        assertEquals(t1Question.getId(), response.getQuestionId());

        assertTrue(bookmarkService.isBookmarked(t1Question.getId()));

        List<Bookmark> list = bookmarkRepository.findAll();
        assertEquals(1, list.size());
        assertFalse(list.get(0).isDeleted());

        // Toggle again (removes bookmark / soft delete)
        bookmarkService.toggleBookmark(t1Question.getId());

        assertFalse(bookmarkService.isBookmarked(t1Question.getId()));
        List<Bookmark> listAfterToggle = bookmarkRepository.findAll();
        assertEquals(1, listAfterToggle.size());
        assertTrue(listAfterToggle.get(0).isDeleted());

        // Toggle third time (restores bookmark)
        bookmarkService.toggleBookmark(t1Question.getId());

        assertTrue(bookmarkService.isBookmarked(t1Question.getId()));
        List<Bookmark> listAfterThirdToggle = bookmarkRepository.findAll();
        assertEquals(1, listAfterThirdToggle.size());
        assertFalse(listAfterThirdToggle.get(0).isDeleted());
    }

    @Test
    public void testExplicitRemoveBookmark() {
        authenticateUser(t1User);

        // Toggle on
        bookmarkService.toggleBookmark(t1Question.getId());
        assertTrue(bookmarkService.isBookmarked(t1Question.getId()));

        // Explicitly remove
        bookmarkService.removeBookmark(t1Question.getId());
        assertFalse(bookmarkService.isBookmarked(t1Question.getId()));
    }

    @Test
    public void testCrossTenantRejection() {
        // Authenticate user on Tenant 2 trying to bookmark Tenant 1 content
        authenticateUser(t2User);

        assertThrows(AccessDeniedException.class, () -> {
            bookmarkService.toggleBookmark(t1Question.getId());
        });
    }

    @Test
    public void testDeletedQuestionRejection() {
        authenticateUser(t1User);

        // Soft delete question
        t1Question.setDeleted(true);
        questionRepository.save(t1Question);

        assertThrows(QuestionNotFoundException.class, () -> {
            bookmarkService.toggleBookmark(t1Question.getId());
        });
    }

    @Test
    public void testGetMyBookmarks() {
        authenticateUser(t1User);

        bookmarkService.toggleBookmark(t1Question.getId());

        Page<BookmarkResponse> bookmarks = bookmarkService.getMyBookmarks(PageRequest.of(0, 10));
        assertEquals(1, bookmarks.getTotalElements());
        assertEquals("T1 Question", bookmarks.getContent().get(0).getQuestionTitle());
    }
}

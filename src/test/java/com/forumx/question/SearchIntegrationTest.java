package com.forumx.question;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.verification.repository.VerificationTokenRepository;
import com.forumx.bookmark.repository.BookmarkRepository;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.question.dto.response.SearchResultResponse;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.question.service.SearchService;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ForumXApplication.class)
@ActiveProfiles("dev")
@Transactional
public class SearchIntegrationTest {

    @Autowired private SearchService searchService;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketMessageRepository ticketMessageRepository;
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private com.forumx.answer.repository.AnswerRepository answerRepository;
    @Autowired private com.forumx.moderation.repository.ModerationReportRepository reportRepository;

    @MockBean private AuthenticationFacade authenticationFacade;
    @MockBean private TenantResolver tenantResolver;

    private Tenant tenant1;
    private Tenant tenant2;
    private User t1User;
    private User t2User;
    private User t1UserDisabled;

    @BeforeEach
    public void setUp() {
        // Complete DB cleanup in correct dependency order
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
        t1User = userRepository.save(User.builder()
                .username("t1_usr")
                .email("t1_usr@test.com")
                .tenant(tenant1)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(UserRole.builder().user(t1User).role(userRole).active(true).build());

        t2User = userRepository.save(User.builder()
                .username("t2_usr")
                .email("t2_usr@test.com")
                .tenant(tenant2)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(UserRole.builder().user(t2User).role(userRole).active(true).build());

        t1UserDisabled = userRepository.save(User.builder()
                .username("disabled_usr")
                .email("disabled@test.com")
                .tenant(tenant1)
                .enabled(false)
                .status(User.UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(UserRole.builder().user(t1UserDisabled).role(userRole).active(true).build());

        t1User = userRepository.findByIdWithFullProfile(t1User.getId()).orElseThrow();
        t2User = userRepository.findByIdWithFullProfile(t2User.getId()).orElseThrow();
        t1UserDisabled = userRepository.findByIdWithFullProfile(t1UserDisabled.getId()).orElseThrow();
    }

    private void authenticateUser(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);
        when(tenantResolver.resolveTenantId()).thenReturn(user.getTenant().getId());
    }

    @Test
    public void testSearchQuestionsFlow() {
        // Authenticate as Tenant 1 user
        authenticateUser(t1User);

        // Save Question 1 on Tenant 1 matching "spring"
        Question q1 = Question.builder()
                .title("Getting started with Spring Security")
                .content("In this tutorial, we will explore Spring Security configuration details.")
                .author(t1User)
                .tenant(tenant1)
                .build();
        q1.setVoteScore(15);
        questionRepository.save(q1);

        // Save Question 2 on Tenant 1 matching "spring" (but higher vote score)
        Question q2 = Question.builder()
                .title("Spring Boot deployment")
                .content("Guide on deploying spring boot application container on GCP Alloydb Omni.")
                .author(t1User)
                .tenant(tenant1)
                .build();
        q2.setVoteScore(30);
        questionRepository.save(q2);

        // Save Question 3 on Tenant 2 matching "spring"
        Question q3 = Question.builder()
                .title("Spring MVC controller mapping")
                .content("Connecting MVC mappings in tenant 2 container.")
                .author(t2User)
                .tenant(tenant2)
                .build();
        questionRepository.save(q3);

        // Save Question 4 on Tenant 1 matching "spring" but deleted
        Question q4 = Question.builder()
                .title("Deleted spring question")
                .content("This question is soft deleted.")
                .author(t1User)
                .tenant(tenant1)
                .build();
        q4.setDeleted(true);
        questionRepository.save(q4);

        // Save Question 5 on Tenant 1 from disabled user
        Question q5 = Question.builder()
                .title("Spring post from disabled")
                .content("Should be hidden.")
                .author(t1UserDisabled)
                .tenant(tenant1)
                .build();
        questionRepository.save(q5);

        // Perform search sorted by voteScore DESC
        Page<SearchResultResponse> searchResults = searchService.searchQuestions(
                "spring", 
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "voteScore"))
        );

        // Verify isolation, exclusions, and sorting
        assertEquals(2, searchResults.getTotalElements());
        
        List<SearchResultResponse> list = searchResults.getContent();
        assertEquals("Spring Boot deployment", list.get(0).getTitle());
        assertEquals("Getting started with Spring Security", list.get(1).getTitle());
    }
}

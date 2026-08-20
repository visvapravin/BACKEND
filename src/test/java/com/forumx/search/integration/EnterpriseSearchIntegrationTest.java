package com.forumx.search.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.question.entity.Question;
import com.forumx.question.entity.QuestionStatus;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.dto.request.QuestionSearchFilter;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.response.SearchPageResponse;
import com.forumx.search.service.SearchApplicationService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ForumXApplication.class)
@ActiveProfiles("dev")
@Transactional
public class EnterpriseSearchIntegrationTest {

    @Autowired private SearchApplicationService searchApplicationService;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;

    @MockBean private AuthenticationFacade authenticationFacade;
    @MockBean private TenantResolver tenantResolver;

    private Tenant tenantA;
    private Tenant tenantB;
    private User userTenantA;
    private User userTenantB;
    private User deletedUserTenantA;

    private Question q1TenantA;
    private Question q2TenantA;
    private Question q3TenantAAnswered;
    private Question q4TenantADeleted;
    private Question q5TenantB;

    @BeforeEach
    public void setUp() {
        // Setup Tenants
        tenantA = tenantRepository.findBySlug("tenant-a-search")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("Tenant A Search").slug("tenant-a-search").build()));

        tenantB = tenantRepository.findBySlug("tenant-b-search")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().name("Tenant B Search").slug("tenant-b-search").build()));

        Role userRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.USER).description("Standard User").build()));

        // Setup Users
        userTenantA = userRepository.findByTenantIdAndUsernameAndDeletedFalse(tenantA.getId(), "user_a_search")
                .orElseGet(() -> {
                    User u = userRepository.save(User.builder()
                            .tenant(tenantA)
                            .username("user_a_search")
                            .email("user_a_search@test.com")
                            .passwordHash("$2a$10$dummyHashForTestingOnly1234567890")
                            .enabled(true)
                            .deleted(false)
                            .build());
                    userRoleRepository.save(UserRole.builder().user(u).role(userRole).build());
                    return u;
                });

        userTenantB = userRepository.findByTenantIdAndUsernameAndDeletedFalse(tenantB.getId(), "user_b_search")
                .orElseGet(() -> {
                    User u = userRepository.save(User.builder()
                            .tenant(tenantB)
                            .username("user_b_search")
                            .email("user_b_search@test.com")
                            .passwordHash("$2a$10$dummyHashForTestingOnly1234567890")
                            .enabled(true)
                            .deleted(false)
                            .build());
                    userRoleRepository.save(UserRole.builder().user(u).role(userRole).build());
                    return u;
                });

        deletedUserTenantA = userRepository.findByTenantIdAndEmailAndDeletedFalse(tenantA.getId(), "deleted_user_a@test.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .tenant(tenantA)
                        .username("deleted_user_a")
                        .email("deleted_user_a@test.com")
                        .passwordHash("$2a$10$dummyHashForTestingOnly1234567890")
                        .enabled(false)
                        .deleted(true)
                        .build()));

        Instant now = Instant.now();

        // Setup Questions for Tenant A
        q1TenantA = questionRepository.save(Question.builder()
                .tenant(tenantA)
                .author(userTenantA)
                .title("PostgreSQL Full Text Search In Spring Boot")
                .content("How do I configure postgres tsvector search in Spring Boot?")
                .status(QuestionStatus.OPEN)
                .viewCount(100)
                .answerCount(5)
                .voteScore(15)
                .deleted(false)
                .createdAt(now.minus(2, ChronoUnit.DAYS))
                .updatedAt(now)
                .version(1L)
                .build());

        q2TenantA = questionRepository.save(Question.builder()
                .tenant(tenantA)
                .author(userTenantA)
                .title("Redis PubSub Integration Guide")
                .content("Guide on configuring Redis PubSub messaging queues")
                .status(QuestionStatus.OPEN)
                .viewCount(50)
                .answerCount(1)
                .voteScore(5)
                .deleted(false)
                .createdAt(now.minus(1, ChronoUnit.DAYS))
                .updatedAt(now)
                .version(1L)
                .build());

        q3TenantAAnswered = questionRepository.save(Question.builder()
                .tenant(tenantA)
                .author(userTenantA)
                .title("Solved Spring Security Multi Tenancy")
                .content("Multi tenancy solved architectural pattern")
                .status(QuestionStatus.ANSWERED)
                .viewCount(200)
                .answerCount(10)
                .voteScore(50)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .version(1L)
                .build());

        q4TenantADeleted = questionRepository.save(Question.builder()
                .tenant(tenantA)
                .author(userTenantA)
                .title("Deleted Question About Database")
                .content("This question is deleted and must never appear in search")
                .status(QuestionStatus.OPEN)
                .viewCount(0)
                .answerCount(0)
                .voteScore(0)
                .deleted(true)
                .createdAt(now)
                .updatedAt(now)
                .version(1L)
                .build());

        // Setup Question for Tenant B (Cross-tenant boundary test)
        q5TenantB = questionRepository.save(Question.builder()
                .tenant(tenantB)
                .author(userTenantB)
                .title("Tenant B Confidential Spring Guide")
                .content("This is Tenant B content about Spring Boot")
                .status(QuestionStatus.OPEN)
                .viewCount(10)
                .answerCount(0)
                .voteScore(1)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .version(1L)
                .build());
    }

    private void mockTenantContext(Tenant tenant, User user) {
        when(tenantResolver.resolveTenantId()).thenReturn(tenant.getId());
        CustomUserDetails details = new CustomUserDetails(user);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);
    }

    private void mockPlatformAdminContext() {
        when(tenantResolver.resolveTenantId()).thenReturn(null);
        User platformAdmin = User.builder()
                .id(9999L)
                .username("platform_admin")
                .email("platform-admin@forumx.local")
                .tenant(null)
                .build();
        CustomUserDetails details = new CustomUserDetails(platformAdmin);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);
    }

    @Test
    public void test1_TenantUserCanSearchOwnTenantQuestions() {
        mockTenantContext(tenantA, userTenantA);

        SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(
                "Spring", null, SearchSort.RELEVANCE, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertFalse(response.content().isEmpty());
        assertTrue(response.content().stream().allMatch(r -> r.authorUsername().equals("user_a_search")));
    }

    @Test
    public void test2_TenantACannotSeeTenantBQuestions() {
        mockTenantContext(tenantA, userTenantA);

        SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(
                "Confidential", null, SearchSort.RELEVANCE, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertTrue(response.content().isEmpty(), "Tenant A user must never see Tenant B questions");
    }

    @Test
    public void test3_PlatformAdminCannotPerformTenantQuestionSearch() {
        mockPlatformAdminContext();

        assertThrows(AccessDeniedException.class, () ->
                searchApplicationService.searchQuestions("Spring", null, SearchSort.RELEVANCE, PageRequest.of(0, 10))
        );
    }

    @Test
    public void test4_MissingTenantContextIsRejectedCleanly() {
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(null);

        assertThrows(AccessDeniedException.class, () ->
                searchApplicationService.searchQuestions("Spring", null, SearchSort.RELEVANCE, PageRequest.of(0, 10))
        );
    }

    @Test
    public void test5_SearchResultAuthorUsernamePopulatedCorrectlyWithoutLazyInitException() {
        mockTenantContext(tenantA, userTenantA);

        SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(
                "PostgreSQL", null, SearchSort.RELEVANCE, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertEquals(1, response.content().size());
        QuestionSearchResult result = response.content().get(0);
        assertEquals("user_a_search", result.authorUsername());
        assertEquals(100, result.viewCount());
        assertEquals(5, result.answerCount());
        assertFalse(result.solved());
    }

    @Test
    public void test6_DeletedQuestionsAreExcluded() {
        mockTenantContext(tenantA, userTenantA);

        SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(
                "Deleted", null, SearchSort.RELEVANCE, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertTrue(response.content().isEmpty(), "Deleted questions must be excluded from search");
    }

    @Test
    public void test7_SearchPaginationAndSortingWorks() {
        mockTenantContext(tenantA, userTenantA);

        // Sort by MOST_ANSWERED
        SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(
                null, null, SearchSort.MOST_ANSWERED, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertTrue(response.content().size() >= 2);
        assertTrue(response.content().get(0).answerCount() >= response.content().get(1).answerCount());
    }

    @Test
    public void test8_SearchSolvedFilterWorks() {
        mockTenantContext(tenantA, userTenantA);

        QuestionSearchFilter solvedFilter = new QuestionSearchFilter(null, true, null, null);
        SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(
                null, solvedFilter, SearchSort.NEWEST, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertTrue(response.content().get(0).solved());
        assertEquals("Solved Spring Security Multi Tenancy", response.content().get(0).title());
    }

    @Test
    public void test9_SearchZeroResultsReturnsEmptyPageNotException() {
        mockTenantContext(tenantA, userTenantA);

        SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(
                "NonExistentKeywordXYZ123456", null, SearchSort.RELEVANCE, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertTrue(response.content().isEmpty());
        assertEquals(0, response.totalElements());
    }

    @Test
    public void test10_SearchSpecialCharactersWorks() {
        mockTenantContext(tenantA, userTenantA);

        assertDoesNotThrow(() -> {
            SearchPageResponse<QuestionSearchResult> response = searchApplicationService.searchQuestions(
                    "Spring & <test> ' \" %", null, SearchSort.RELEVANCE, PageRequest.of(0, 10)
            );
            assertNotNull(response);
        });
    }
}

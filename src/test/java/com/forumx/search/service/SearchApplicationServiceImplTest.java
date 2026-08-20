package com.forumx.search.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.forumx.auth.entity.User;
import com.forumx.search.config.SearchProperties;
import com.forumx.search.domain.QuestionSearchResult;
import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.response.SearchPageResponse;
import com.forumx.search.metrics.SearchMetricsService;
import com.forumx.search.service.impl.SearchApplicationServiceImpl;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
public class SearchApplicationServiceImplTest {

    @Mock private SearchService searchService;
    @Mock private TenantResolver tenantResolver;
    @Mock private AuthenticationFacade authenticationFacade;
    @Mock private SearchMetricsService searchMetricsService;

    private SearchApplicationService appService;
    private SearchProperties searchProperties;

    @BeforeEach
    public void setUp() {
        searchProperties = new SearchProperties();
        appService = new SearchApplicationServiceImpl(
                searchService,
                tenantResolver,
                authenticationFacade,
                searchMetricsService,
                searchProperties
        );
    }

    @Test
    public void testSearchUsersMasksEmailForNormalUser() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        User user = User.builder().id(10L).tenant(Tenant.builder().id(1L).build()).username("normal").email("normal@test.com").build();
        CustomUserDetails userDetails = new CustomUserDetails(user); // Regular USER authority
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(userDetails);

        UserSearchResult maskedResult = new UserSearchResult(10L, "normal", null, true, Instant.now());
        when(searchService.searchUsers(eq(1L), eq("normal"), any(), eq(SearchSort.RELEVANCE), any(), eq(false)))
                .thenReturn(new PageImpl<>(List.of(maskedResult)));

        SearchPageResponse<UserSearchResult> response = appService.searchUsers(
                "normal", null, SearchSort.RELEVANCE, PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertNull(response.content().get(0).email());
        verify(searchService).searchUsers(eq(1L), eq("normal"), any(), eq(SearchSort.RELEVANCE), any(), eq(false));
    }

    @Test
    public void testSearchUsersIncludesEmailForAdminUser() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        CustomUserDetails adminDetails = mock(CustomUserDetails.class);
        when(adminDetails.getTenantId()).thenReturn(1L);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(adminDetails).getAuthorities();
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(adminDetails);

        UserSearchResult unmaskedResult = new UserSearchResult(10L, "normal", "normal@test.com", true, Instant.now());
        when(searchService.searchUsers(eq(1L), eq("normal"), any(), eq(SearchSort.RELEVANCE), any(), eq(true)))
                .thenReturn(new PageImpl<>(List.of(unmaskedResult)));

        SearchPageResponse<UserSearchResult> response = appService.searchUsers(
                "normal", null, SearchSort.RELEVANCE, PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("normal@test.com", response.content().get(0).email());
        verify(searchService).searchUsers(eq(1L), eq("normal"), any(), eq(SearchSort.RELEVANCE), any(), eq(true));
    }

    @Test
    public void testPlatformAdminSearchUsersSearchesPlatformWide() {
        CustomUserDetails platformAdmin = mock(CustomUserDetails.class);
        when(platformAdmin.getTenantId()).thenReturn(null);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))).when(platformAdmin).getAuthorities();
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(platformAdmin);

        UserSearchResult userResult = new UserSearchResult(10L, "normal", "normal@test.com", true, Instant.now());
        when(searchService.searchUsers(isNull(), eq("normal"), any(), eq(SearchSort.RELEVANCE), any(), eq(true)))
                .thenReturn(new PageImpl<>(List.of(userResult)));

        SearchPageResponse<UserSearchResult> response = appService.searchUsers(
                "normal", null, SearchSort.RELEVANCE, PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(1, response.content().size());
        verify(searchService).searchUsers(isNull(), eq("normal"), any(), eq(SearchSort.RELEVANCE), any(), eq(true));
    }

    @Test
    public void testPlatformAdminSearchQuestionsThrowsAccessDenied() {
        CustomUserDetails platformAdmin = mock(CustomUserDetails.class);
        when(platformAdmin.getTenantId()).thenReturn(null);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(platformAdmin);

        assertThrows(AccessDeniedException.class, () ->
                appService.searchQuestions("test", null, SearchSort.RELEVANCE, PageRequest.of(0, 10))
        );
        verifyNoInteractions(searchService);
    }

    @Test
    public void testTenantMismatchedSearchQuestionsThrowsAccessDenied() {
        when(tenantResolver.resolveTenantId()).thenReturn(2L);
        CustomUserDetails tenant1User = mock(CustomUserDetails.class);
        when(tenant1User.getTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(tenant1User);

        assertThrows(AccessDeniedException.class, () ->
                appService.searchQuestions("test", null, SearchSort.RELEVANCE, PageRequest.of(0, 10))
        );
        verifyNoInteractions(searchService);
    }

    @Test
    public void testTenantUserSearchQuestionsSuccess() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        CustomUserDetails tenant1User = mock(CustomUserDetails.class);
        when(tenant1User.getTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(tenant1User);

        QuestionSearchResult qResult = new QuestionSearchResult(
                100L, "Spring Boot Search", "Snippet", "author_user", false, 5, 2, Instant.now()
        );
        when(searchService.searchQuestions(eq(1L), eq("spring"), any(), eq(SearchSort.RELEVANCE), any()))
                .thenReturn(new PageImpl<>(List.of(qResult)));

        SearchPageResponse<QuestionSearchResult> response = appService.searchQuestions(
                "spring", null, SearchSort.RELEVANCE, PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("Spring Boot Search", response.content().get(0).title());
        assertEquals("author_user", response.content().get(0).authorUsername());
    }
}

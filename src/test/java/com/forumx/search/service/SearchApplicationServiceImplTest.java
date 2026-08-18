package com.forumx.search.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.forumx.auth.entity.User;
import com.forumx.search.config.SearchProperties;
import com.forumx.search.domain.UserSearchResult;
import com.forumx.search.dto.request.SearchSort;
import com.forumx.search.dto.response.SearchPageResponse;
import com.forumx.search.metrics.SearchMetricsService;
import com.forumx.search.service.impl.SearchApplicationServiceImpl;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
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
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
    }

    @Test
    public void testSearchUsersMasksEmailForNormalUser() {
        User user = User.builder().id(10L).username("normal").email("normal@test.com").build();
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
        CustomUserDetails adminDetails = mock(CustomUserDetails.class);
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
}

package com.forumx.question.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.common.exception.InvalidSearchQueryException;
import com.forumx.question.dto.response.SearchResultResponse;
import com.forumx.question.service.SearchService;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.security.service.CustomUserDetailsService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import com.forumx.ForumXApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ForumXApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private SearchService searchService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private TenantResolver tenantResolver;

    private User mockUser;
    private CustomUserDetails customUserDetails;
    private String userToken;

    @BeforeEach
    public void setUp() {
        Tenant tenant = Tenant.builder()
                .id(1L)
                .name("Default Tenant")
                .slug("default")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();

        Role userRole = Role.builder().id(1L).roleName(RoleType.USER).active(true).build();

        mockUser = User.builder()
                .id(2L)
                .username("visva")
                .email("visva@test.com")
                .passwordHash("hashedPassword")
                .tenant(tenant)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        mockUser.setUserRoles(Set.of(UserRole.builder().user(mockUser).role(userRole).active(true).build()));

        customUserDetails = new CustomUserDetails(mockUser);

        lenient().when(tenantResolver.resolveTenantId()).thenReturn(1L);
        lenient().when(userDetailsService.loadUserByUsername("visva")).thenReturn(customUserDetails);

        userToken = jwtTokenProvider.generateAccessToken(customUserDetails);
    }

    @Test
    public void testSearchQuestionsSuccess() throws Exception {
        SearchResultResponse result = SearchResultResponse.builder()
                .questionId(100L)
                .title("Java 21 Features")
                .shortDescription("Discussing Java 21 features")
                .authorName("visva")
                .voteCount(5)
                .answerCount(2)
                .createdAt(Instant.now())
                .build();

        Pageable pageable = PageRequest.of(0, 20);
        when(searchService.searchQuestions(eq("spring"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(result), pageable, 1));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "spring")
                        .param("page", "0")
                        .param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].questionId").value(100L))
                .andExpect(jsonPath("$.content[0].title").value("Java 21 Features"))
                .andExpect(jsonPath("$.content[0].authorName").value("visva"));
    }

    @Test
    public void testSearchQuestions_BlankQueryReturnsBadRequest() throws Exception {
        when(searchService.searchQuestions(eq(""), any(Pageable.class)))
                .thenThrow(new InvalidSearchQueryException("Search query must not be blank"));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Search query must not be blank"));
    }

    @Test
    public void testSearchQuestions_InvalidSortPropertyReturnsBadRequest() throws Exception {
        when(searchService.searchQuestions(eq("spring"), any(Pageable.class)))
                .thenThrow(new InvalidSearchQueryException("Sorting by property 'id' is not supported"));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "spring")
                        .param("sort", "id,desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sorting by property 'id' is not supported"));
    }

    @Test
    public void testSearchQuestions_UnauthenticatedReturnsForbiddenOrUnauthorized() throws Exception {
        // Accessing without JWT token
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "spring"))
                .andExpect(status().isUnauthorized());
    }
}

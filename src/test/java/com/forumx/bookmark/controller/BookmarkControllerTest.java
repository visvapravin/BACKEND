package com.forumx.bookmark.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.bookmark.dto.response.BookmarkResponse;
import com.forumx.bookmark.service.BookmarkService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class BookmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private BookmarkService bookmarkService;

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

        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(userDetailsService.loadUserByUsername("visva")).thenReturn(customUserDetails);

        userToken = jwtTokenProvider.generateAccessToken(customUserDetails);
    }

    @Test
    public void testToggleBookmarkSuccess() throws Exception {
        BookmarkResponse response = BookmarkResponse.builder()
                .questionId(100L)
                .questionTitle("Java 21 Features")
                .createdAt(Instant.now())
                .build();

        when(bookmarkService.toggleBookmark(100L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/questions/100/bookmark")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(100L))
                .andExpect(jsonPath("$.questionTitle").value("Java 21 Features"));
    }

    @Test
    public void testRemoveBookmarkSuccess() throws Exception {
        doNothing().when(bookmarkService).removeBookmark(100L);

        mockMvc.perform(delete("/api/v1/questions/100/bookmark")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testGetMyBookmarksSuccess() throws Exception {
        BookmarkResponse response = BookmarkResponse.builder()
                .questionId(100L)
                .questionTitle("Java 21 Features")
                .createdAt(Instant.now())
                .build();

        when(bookmarkService.getMyBookmarks(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(response)));

        mockMvc.perform(get("/api/v1/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].questionId").value(100L))
                .andExpect(jsonPath("$.content[0].questionTitle").value("Java 21 Features"));
    }

    @Test
    public void testIsBookmarkedSuccess() throws Exception {
        when(bookmarkService.isBookmarked(100L)).thenReturn(true);

        mockMvc.perform(get("/api/v1/questions/100/bookmark")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    public void testToggleBookmarkWithoutAuth_Throws401() throws Exception {
        mockMvc.perform(post("/api/v1/questions/100/bookmark"))
                .andExpect(status().isUnauthorized());
    }
}

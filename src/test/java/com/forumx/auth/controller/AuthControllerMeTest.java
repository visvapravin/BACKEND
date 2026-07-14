package com.forumx.auth.controller;

import com.forumx.auth.dto.response.CurrentUserResponse;
import com.forumx.tenant.entity.Tenant;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserProfile;
import com.forumx.auth.service.AuthenticationService;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.security.service.CustomUserDetailsService;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class AuthControllerMeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private TenantResolver tenantResolver;

    private User mockUser;
    private CustomUserDetails customUserDetails;

    @BeforeEach
    public void setUp() {
        Tenant tenant = Tenant.builder()
                .id(1L)
                .name("Default Tenant")
                .slug("default")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();

        mockUser = User.builder()
                .id(2L)
                .username("visva")
                .email("visva@test.com")
                .passwordHash("hashedPassword")
                .tenant(tenant)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build();

        UserProfile userProfile = UserProfile.builder()
                .id(10L)
                .user(mockUser)
                .displayName("Visva Pravin")
                .firstName("Visva")
                .lastName("Pravin")
                .build();

        mockUser.setUserProfile(userProfile);

        customUserDetails = new CustomUserDetails(mockUser);
    }

    @Test
    public void testGetMeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetMeWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetMeWithValidTokenReturns200() throws Exception {
        // Mock tenant resolution to match the token tenant ID
        when(tenantResolver.resolveTenantId()).thenReturn(1L);

        // Mock UserDetails loading for validation
        when(userDetailsService.loadUserByUsername("visva")).thenReturn(customUserDetails);

        // Generate token
        String validToken = jwtTokenProvider.generateAccessToken(customUserDetails);

        // Mock service layer
        CurrentUserResponse response = CurrentUserResponse.builder()
                .userId(2L)
                .username("visva")
                .email("visva@test.com")
                .displayName("Visva Pravin")
                .firstName("Visva")
                .lastName("Pravin")
                .tenantId(1L)
                .tenantSlug("default")
                .active(true)
                .enabled(true)
                .accountNonLocked(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .roles(List.of("USER"))
                .permissions(List.of())
                .build();

        when(authenticationService.getCurrentUser(2L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .header("X-Tenant", "default")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Current user retrieved successfully"))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.username").value("visva"))
                .andExpect(jsonPath("$.data.displayName").value("Visva Pravin"))
                .andExpect(jsonPath("$.data.email").value("visva@test.com"))
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.tenantSlug").value("default"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }
}

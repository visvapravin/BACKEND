package com.forumx.support.ticket;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.ForumXApplication;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest(classes = ForumXApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class SupportTicketAuthorizationIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtTokenProvider jwt;

    private static final String PASSWORD = "TestPassword123!";
    private Tenant tenantA;
    private Tenant tenantB;
    private User userA;
    private User userB;
    private User platformAdmin;
    private String tokenUserA;
    private String tokenUserB;
    private String tokenPlatformAdmin;

    @BeforeEach
    void setUp() {
        tenantA = tenantRepository.save(Tenant.builder()
                .name("Tenant Alpha " + System.nanoTime())
                .slug("tenant-a-" + System.nanoTime())
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        tenantB = tenantRepository.save(Tenant.builder()
                .name("Tenant Beta " + System.nanoTime())
                .slug("tenant-b-" + System.nanoTime())
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        Role roleUser = roleRepository.findByRoleName(RoleType.USER).orElseThrow();
        Role rolePlatformAdmin = roleRepository.findByRoleName(RoleType.PLATFORM_ADMIN).orElseThrow();

        // User A belongs to Tenant A only
        userA = userRepository.save(User.builder()
                .username("user_a_" + System.nanoTime())
                .email("user_a_" + System.nanoTime() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        UserRole roleA = userRoleRepository.save(UserRole.builder().user(userA).tenant(tenantA).role(roleUser).active(true).build());

        // User B belongs to Tenant B only
        userB = userRepository.save(User.builder()
                .username("user_b_" + System.nanoTime())
                .email("user_b_" + System.nanoTime() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        UserRole roleB = userRoleRepository.save(UserRole.builder().user(userB).tenant(tenantB).role(roleUser).active(true).build());

        // Platform Admin (Global, no tenant)
        platformAdmin = userRepository.save(User.builder()
                .username("platform_admin_" + System.nanoTime())
                .email("platform_admin_" + System.nanoTime() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        UserRole rolePA = userRoleRepository.save(UserRole.builder().user(platformAdmin).role(rolePlatformAdmin).active(true).build());

        // Generate tokens
        tokenUserA = jwt.generateAccessToken(new com.forumx.security.model.CustomUserDetails(userA, tenantA.getId(), tenantA.getSlug(), List.of(roleA)));
        tokenUserB = jwt.generateAccessToken(new com.forumx.security.model.CustomUserDetails(userB, tenantB.getId(), tenantB.getSlug(), List.of(roleB)));
        tokenPlatformAdmin = jwt.generateAccessToken(new com.forumx.security.model.CustomUserDetails(platformAdmin, null, null, List.of(rolePA)));
    }

    @Test
    @DisplayName("TEST 1: Normal USER in Tenant A creates support ticket -> 201 Created")
    void test1_userCreatesSupportTicketInOwnTenant_success() throws Exception {
        CreateTicketRequest req = CreateTicketRequest.builder()
                .subject("Cannot reset 2FA")
                .description("Please assist with 2FA token reset")
                .priority(TicketPriority.HIGH)
                .build();

        mvc.perform(post("/api/v1/support/tickets")
                        .header("Authorization", "Bearer " + tokenUserA)
                        .header("X-Tenant", tenantA.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subject").value("Cannot reset 2FA"))
                .andExpect(jsonPath("$.data.tenantId").value(tenantA.getId()));

        assertEquals(1, ticketRepository.countByTenant_IdAndStatusAndDeletedFalse(tenantA.getId(),
                com.forumx.support.ticket.entity.TicketStatus.OPEN));
    }

    @Test
    @DisplayName("TEST 2: Normal USER in Tenant A attempts ticket creation under Tenant B -> 403 Forbidden")
    void test2_userAttemptsTicketInOtherTenant_forbidden() throws Exception {
        CreateTicketRequest req = CreateTicketRequest.builder()
                .subject("Cross tenant injection attempt")
                .description("Should be rejected")
                .priority(TicketPriority.LOW)
                .build();

        // Sending Token A with Tenant B header
        mvc.perform(post("/api/v1/support/tickets")
                        .header("Authorization", "Bearer " + tokenUserA)
                        .header("X-Tenant", tenantB.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST 5: PLATFORM_ADMIN attempts ticket creation -> 403 Forbidden")
    void test5_platformAdminAttemptsTicketCreation_forbidden() throws Exception {
        CreateTicketRequest req = CreateTicketRequest.builder()
                .subject("Platform ticket")
                .description("Platform admin cannot access tenant tickets")
                .priority(TicketPriority.LOW)
                .build();

        mvc.perform(post("/api/v1/support/tickets")
                        .header("Authorization", "Bearer " + tokenPlatformAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}

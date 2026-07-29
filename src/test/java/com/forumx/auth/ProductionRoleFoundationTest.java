package com.forumx.auth;

import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.dto.response.RegisterResponse;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.service.AuthenticationService;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class ProductionRoleFoundationTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.forumx.notification.publisher.NotificationPublisher notificationPublisher;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Tenant defaultTenant;

    @BeforeEach
    void setUp() {
        // Ensure USER role exists for registration (V3 seeds it; V35 guarantees it)
        roleRepository.findByRoleName(RoleType.USER)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.USER).active(true).build()));

        // Use the Flyway-seeded "default" tenant or create it if absent
        defaultTenant = tenantRepository.findBySlugAndDeletedFalse("default")
                .orElseGet(() -> {
                    Tenant tenant = Tenant.builder()
                            .name("Default Tenant")
                            .slug("default")
                            .status(Tenant.TenantStatus.ACTIVE)
                            .subscriptionPlan(Tenant.SubscriptionPlan.FREE)
                            .maxUsers(100)
                            .storageQuotaMB(1024L)
                            .timezone("UTC")
                            .locale("en_US")
                            .build();
                    return tenantRepository.save(tenant);
                });
    }

    @Test
    @DisplayName("A1: Public registration with 'admin' in username must assign USER role only")
    void testRegisterWithAdminUsername_AssignsUserRoleOnly() {
        RegisterRequest request = RegisterRequest.builder()
                .tenantSlug("default")
                .username("admin_test_user")
                .email("admin_test@forumx.local")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        RegisterResponse response = authenticationService.register(request);
        assertNotNull(response);

        User user = userRepository.findByTenantIdAndUsernameAndDeletedFalse(defaultTenant.getId(), "admin_test_user")
                .orElseThrow();

        // Query roles directly — user.getUserRoles() is lazy and not populated on the transient entity
        List<RoleType> roleNames = userRoleRepository.findAll().stream()
                .filter(ur -> ur.getUser().getId().equals(user.getId()) && ur.isActive())
                .map(ur -> ur.getRole().getRoleName())
                .collect(Collectors.toList());

        assertEquals(1, roleNames.size(), "Registered user must have exactly 1 role");
        assertEquals(RoleType.USER, roleNames.get(0), "Role must be USER even if username contains 'admin'");
    }

    @Test
    @DisplayName("A1: Public registration with 'moderator' in username must assign USER role only")
    void testRegisterWithModeratorUsername_AssignsUserRoleOnly() {
        RegisterRequest request = RegisterRequest.builder()
                .tenantSlug("default")
                .username("moderator_test_user")
                .email("moderator_test@forumx.local")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        RegisterResponse response = authenticationService.register(request);
        assertNotNull(response);

        User user = userRepository.findByTenantIdAndUsernameAndDeletedFalse(defaultTenant.getId(), "moderator_test_user")
                .orElseThrow();

        // Query roles directly — user.getUserRoles() is lazy and not populated on the transient entity
        List<RoleType> roleNames = userRoleRepository.findAll().stream()
                .filter(ur -> ur.getUser().getId().equals(user.getId()) && ur.isActive())
                .map(ur -> ur.getRole().getRoleName())
                .collect(Collectors.toList());

        assertEquals(1, roleNames.size(), "Registered user must have exactly 1 role");
        assertEquals(RoleType.USER, roleNames.get(0), "Role must be USER even if username contains 'moderator'");
    }

    @Test
    @DisplayName("A1: Public registration with normal username must assign USER role")
    void testRegisterNormalUsername_AssignsUserRole() {
        RegisterRequest request = RegisterRequest.builder()
                .tenantSlug("default")
                .username("normal_john")
                .email("normal_john@forumx.local")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        RegisterResponse response = authenticationService.register(request);
        assertNotNull(response);

        User user = userRepository.findByTenantIdAndUsernameAndDeletedFalse(defaultTenant.getId(), "normal_john")
                .orElseThrow();

        assertNotNull(user.getTenant(), "Registered USER must be tenant-scoped");
        assertEquals(defaultTenant.getId(), user.getTenant().getId(), "Registered USER must belong to the resolved tenant");

        // Query roles directly — user.getUserRoles() is lazy and not populated on the transient entity
        List<RoleType> roleNames = userRoleRepository.findAll().stream()
                .filter(ur -> ur.getUser().getId().equals(user.getId()) && ur.isActive())
                .map(ur -> ur.getRole().getRoleName())
                .collect(Collectors.toList());

        assertEquals(1, roleNames.size());
        assertEquals(RoleType.USER, roleNames.get(0));
    }

    @Test
    @DisplayName("D0.5: Registration user creation rejects tenantless USER before persistence")
    void testRegistrationUserCreationRejectsTenantlessUserBeforePersistence() {
        long userCountBefore = userRepository.count();
        long userRoleCountBefore = userRoleRepository.count();

        RegisterRequest request = RegisterRequest.builder()
                .tenantSlug("default")
                .username("tenantless_user")
                .email("tenantless_user@forumx.local")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        Object authenticationServiceTarget = AopTestUtils.getTargetObject(authenticationService);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        authenticationServiceTarget,
                        "createUser",
                        request,
                        null,
                        passwordEncoder.encode("Password123!")));

        assertEquals("Tenant-scoped accounts require a tenant", exception.getMessage());
        assertEquals(userCountBefore, userRepository.count(), "Rejected tenantless USER must not be persisted");
        assertEquals(userRoleCountBefore, userRoleRepository.count(), "Rejected tenantless USER must not receive a role");
        assertTrue(userRepository.findAll().stream()
                        .noneMatch(user -> "tenantless_user".equals(user.getUsername())),
                "Rejected tenantless USER must not leave an account row behind");
    }

    @Test
    @DisplayName("A3: Target production roles TENANT_ADMIN and PLATFORM_ADMIN exist in repository")
    void testTargetProductionRolesExist() {
        assertTrue(roleRepository.findByRoleName(RoleType.TENANT_ADMIN).isPresent(), "TENANT_ADMIN role must exist in DB");
        assertTrue(roleRepository.findByRoleName(RoleType.PLATFORM_ADMIN).isPresent(), "PLATFORM_ADMIN role must exist in DB");
        assertTrue(roleRepository.findByRoleName(RoleType.MODERATOR).isPresent(), "MODERATOR role must exist in DB");
        assertTrue(roleRepository.findByRoleName(RoleType.USER).isPresent(), "USER role must exist in DB");
    }

    @Test
    @DisplayName("A9: JWT generation includes TENANT_ADMIN and PLATFORM_ADMIN claims without legacy names")
    void testJwtClaimsForProductionRoles() {
        Role tenantAdminRole = roleRepository.findByRoleName(RoleType.TENANT_ADMIN).orElseThrow();
        Role platformAdminRole = roleRepository.findByRoleName(RoleType.PLATFORM_ADMIN).orElseThrow();

        User tenantAdminUser = User.builder()
                .tenant(defaultTenant)
                .username("t_admin")
                .email("t_admin@forumx.local")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.save(tenantAdminUser);

        UserRole ur1 = UserRole.builder().user(tenantAdminUser).role(tenantAdminRole).active(true).build();
        userRoleRepository.save(ur1);
        tenantAdminUser.getUserRoles().add(ur1);

        CustomUserDetails userDetails = new CustomUserDetails(tenantAdminUser);
        String token = jwtTokenProvider.generateAccessToken(userDetails);

        List<String> rolesInJwt = jwtTokenProvider.extractRoles(token);
        assertTrue(rolesInJwt.contains("TENANT_ADMIN"), "JWT claims must include TENANT_ADMIN");
        assertFalse(rolesInJwt.contains("ADMIN"), "JWT claims must NOT include legacy ADMIN string");

        User platformAdminUser = User.builder()
                .tenant(defaultTenant)
                .username("p_admin")
                .email("p_admin@forumx.local")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepository.save(platformAdminUser);

        UserRole ur2 = UserRole.builder().user(platformAdminUser).role(platformAdminRole).active(true).build();
        userRoleRepository.save(ur2);
        platformAdminUser.getUserRoles().add(ur2);

        CustomUserDetails pDetails = new CustomUserDetails(platformAdminUser);
        String pToken = jwtTokenProvider.generateAccessToken(pDetails);

        List<String> pRolesInJwt = jwtTokenProvider.extractRoles(pToken);
        assertTrue(pRolesInJwt.contains("PLATFORM_ADMIN"), "JWT claims must include PLATFORM_ADMIN");
        assertFalse(pRolesInJwt.contains("SUPER_ADMIN"), "JWT claims must NOT include legacy SUPER_ADMIN string");
    }
}

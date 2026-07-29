package com.forumx.platform.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.service.AccountScopeValidator;
import com.forumx.ForumXApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ForumXApplication.class)
@ActiveProfiles("dev")
@Transactional
class PlatformAdminBootstrapIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountScopeValidator accountScopeValidator;

    @BeforeEach
    void setup() {
        roleRepository.findByRoleName(RoleType.PLATFORM_ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().roleName(RoleType.PLATFORM_ADMIN).active(true).build()));
    }

    @Test
    void testBootstrapSuccessAndIdempotency() {
        PlatformAdminBootstrapProperties properties = new PlatformAdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setUsername("bootstrapped_admin_" + System.currentTimeMillis());
        properties.setEmail("bootstrapped_admin_" + System.currentTimeMillis() + "@forumx.local");
        properties.setPassword("BootstrapPass123!");

        PlatformAdminBootstrapper bootstrapper = new PlatformAdminBootstrapper(
                properties, userRepository, roleRepository, userRoleRepository, passwordEncoder, accountScopeValidator);

        // First run -> creates PLATFORM_ADMIN
        bootstrapper.run();

        User created = userRepository.findPlatformUserForAuthentication(properties.getUsername()).orElse(null);
        assertNotNull(created, "Platform admin user should be created");
        assertNull(created.getTenant(), "Platform admin tenant must be null");
        assertTrue(created.isEnabled());
        assertEquals(User.UserStatus.ACTIVE, created.getStatus());
        assertTrue(passwordEncoder.matches("BootstrapPass123!", created.getPasswordHash()));

        String originalHash = created.getPasswordHash();

        // Second run -> idempotent, no duplicates created
        bootstrapper.run();

        User afterSecondRun = userRepository.findPlatformUserForAuthentication(properties.getUsername()).orElse(null);
        assertNotNull(afterSecondRun);
        assertEquals(originalHash, afterSecondRun.getPasswordHash());
    }

    @Test
    void testBootstrapDisabledNoCreation() {
        PlatformAdminBootstrapProperties properties = new PlatformAdminBootstrapProperties();
        properties.setEnabled(false);
        properties.setUsername("disabled_admin_" + System.currentTimeMillis());
        properties.setEmail("disabled_admin_" + System.currentTimeMillis() + "@forumx.local");
        properties.setPassword("BootstrapPass123!");

        PlatformAdminBootstrapper bootstrapper = new PlatformAdminBootstrapper(
                properties, userRepository, roleRepository, userRoleRepository, passwordEncoder, accountScopeValidator);

        bootstrapper.run();

        assertFalse(userRepository.findPlatformUserForAuthentication(properties.getUsername()).isPresent());
    }

    @Test
    void testBootstrapInvalidConfigFailsSafely() {
        PlatformAdminBootstrapProperties properties = new PlatformAdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setUsername(""); // Invalid
        properties.setEmail("test@forumx.local");
        properties.setPassword("Pass123!");

        PlatformAdminBootstrapper bootstrapper = new PlatformAdminBootstrapper(
                properties, userRepository, roleRepository, userRoleRepository, passwordEncoder, accountScopeValidator);

        assertThrows(IllegalStateException.class, bootstrapper::run);
    }
}

package com.forumx.auth.seeder;

import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
public class DevPersonaSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Tenant tenant = tenantRepository.findBySlugAndDeletedFalse("default").orElse(null);
            if (tenant == null) {
                log.info("Default tenant not present, skipping DevPersonaSeeder.");
                return;
            }

            Role userRole = roleRepository.findByRoleName(RoleType.USER).orElse(null);
            Role modRole = roleRepository.findByRoleName(RoleType.MODERATOR).orElse(null);

            if (userRole == null || modRole == null) {
                log.warn("Roles not found for seeding personas.");
                return;
            }

            String encodedPassword = passwordEncoder.encode("Password123!");

            seedUser("customer_a", "customer_a@forumx.dev", tenant.getId(), userRole.getId(), encodedPassword);
            seedUser("customer_b", "customer_b@forumx.dev", tenant.getId(), userRole.getId(), encodedPassword);
            seedUser("moderator_a", "moderator_a@forumx.dev", tenant.getId(), modRole.getId(), encodedPassword);
            seedUser("moderator_b", "moderator_b@forumx.dev", tenant.getId(), modRole.getId(), encodedPassword);
        } catch (Exception e) {
            log.warn("DevPersonaSeeder encountered an issue during startup: {}", e.getMessage());
        }
    }

    private void seedUser(String username, String email, Long tenantId, Long roleId, String encodedPassword) {
        try {
            Optional<User> existingOpt = userRepository.findByTenantIdAndUsernameAndDeletedFalse(tenantId, username);
            if (existingOpt.isPresent()) {
                User existing = existingOpt.get();
                boolean updated = false;
                if (existing.getPasswordHash() == null || !passwordEncoder.matches("Password123!", existing.getPasswordHash())) {
                    existing.setPasswordHash(encodedPassword);
                    updated = true;
                }
                if (!existing.isEmailVerified() || !existing.isEnabled() || existing.getStatus() != User.UserStatus.ACTIVE) {
                    existing.setEmailVerified(true);
                    existing.setEnabled(true);
                    existing.setStatus(User.UserStatus.ACTIVE);
                    updated = true;
                }
                if (updated) {
                    userRepository.save(existing);
                    log.info("Updated dev persona credentials & active state: username={}", username);
                }
                return;
            }

            Tenant tenantRef = tenantRepository.findById(tenantId).orElse(null);
            Role roleRef = roleRepository.findById(roleId).orElse(null);
            if (tenantRef == null || roleRef == null) return;

            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash(encodedPassword);
            user.setTenant(tenantRef);
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setStatus(User.UserStatus.ACTIVE);

            User savedUser = userRepository.save(user);

            UserRole userRole = new UserRole();
            userRole.setUser(savedUser);
            userRole.setRole(roleRef);
            userRole.setActive(true);
            userRoleRepository.save(userRole);

            savedUser.getUserRoles().add(userRole);

            log.info("Seeded dev persona: username={}, role={}", username, roleRef.getRoleName());
        } catch (Exception e) {
            log.warn("Skipping dev persona seeding for username={} due to: {}", username, e.getMessage());
        }
    }
}

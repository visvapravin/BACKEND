package com.forumx.platform.bootstrap;
import com.forumx.auth.entity.*; import com.forumx.auth.enums.RoleType; import com.forumx.auth.repository.*; import com.forumx.auth.service.AccountScopeValidator;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.boot.CommandLineRunner; import org.springframework.boot.context.properties.EnableConfigurationProperties; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;
@Slf4j @Component @RequiredArgsConstructor @EnableConfigurationProperties(PlatformAdminBootstrapProperties.class)
public class PlatformAdminBootstrapper implements CommandLineRunner {
 private final PlatformAdminBootstrapProperties properties; private final UserRepository users; private final RoleRepository roles; private final UserRoleRepository userRoles; private final PasswordEncoder passwords; private final AccountScopeValidator scopeValidator;
 @Override @Transactional public void run(String... args) { if (!properties.isEnabled() || users.existsPlatformAdmin()) return;
  if (blank(properties.getEmail()) || blank(properties.getUsername()) || blank(properties.getPassword())) throw new IllegalStateException("Platform bootstrap is enabled but required credentials are missing");
  scopeValidator.validate(RoleType.PLATFORM_ADMIN, null); Role role=roles.findByRoleName(RoleType.PLATFORM_ADMIN).orElseThrow(() -> new IllegalStateException("PLATFORM_ADMIN role missing"));
  User user=users.save(User.builder().username(properties.getUsername()).email(properties.getEmail()).passwordHash(passwords.encode(properties.getPassword())).enabled(true).emailVerified(true).status(User.UserStatus.ACTIVE).build());
  userRoles.save(UserRole.builder().user(user).role(role).active(true).build()); log.info("PLATFORM_ADMIN_BOOTSTRAPPED userId={}", user.getId()); }
 private boolean blank(String s){return s==null||s.isBlank();}
}

package com.forumx.security.service;

import java.util.List;

import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final TenantRepository tenantRepository;
    private final TenantResolver tenantResolver;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Long tenantId = tenantResolver.resolveTenantId();
        return loadUserByTenantIdAndUsername(tenantId, username);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserByTenantIdAndUsername(Long tenantId, String username) throws UsernameNotFoundException {
        if (tenantId == null) {
            tenantId = tenantResolver.resolveTenantId();
        }
        final Long effectiveTenantId = tenantId;
        User user = userRepository.findForAuthenticationByTenantIdAndUsernameOrEmail(effectiveTenantId, username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("User not found with username/email: %s for tenant: %d", username, effectiveTenantId)
                ));

        List<UserRole> activeRoles = userRoleRepository.findActiveRolesByUserIdAndTenantId(user.getId(), effectiveTenantId);
        String tenantSlug = tenantRepository.findById(effectiveTenantId)
                .map(Tenant::getSlug)
                .orElse(null);

        return new CustomUserDetails(user, effectiveTenantId, tenantSlug, activeRoles);
    }

    @Transactional(readOnly = true)
    public UserDetails loadPlatformUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findPlatformUserForAuthentication(username)
                .orElseThrow(() -> new UsernameNotFoundException("Platform user not found"));

        List<UserRole> activePlatformRoles = userRoleRepository.findActivePlatformRolesByUserId(user.getId());
        return new CustomUserDetails(user, null, null, activePlatformRoles);
    }
}

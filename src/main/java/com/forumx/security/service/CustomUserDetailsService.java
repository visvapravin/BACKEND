package com.forumx.security.service;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.resolver.TenantResolver;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final TenantResolver tenantResolver;

    public CustomUserDetailsService(UserRepository userRepository, TenantResolver tenantResolver) {
        this.userRepository = userRepository;
        this.tenantResolver = tenantResolver;
    }

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

        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadPlatformUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findPlatformUserForAuthentication(username)
                .orElseThrow(() -> new UsernameNotFoundException("Platform user not found"));
        return new CustomUserDetails(user);
    }
}

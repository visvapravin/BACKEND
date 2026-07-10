package com.forumx.security.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import com.forumx.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

    private final User user;
    private final Set<GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.user = user;
        this.authorities = Collections.unmodifiableSet(buildAuthorities());
    }

    // ── UserDetails contract ────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return !user.isAccountExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isAccountLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !user.isCredentialsExpired();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    // ── Accessor ────────────────────────────────────────────────────────

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    public Long getTenantId() {
        return user.getTenant().getId();
    }

    // ── Authority builder ───────────────────────────────────────────────

    private Set<GrantedAuthority> buildAuthorities() {
        if (user.getUserRoles() == null) {
            return Set.of();
        }

        return user.getUserRoles().stream()
                .filter(userRole -> userRole.isActive())
                .map(userRole -> userRole.getRole())
                .filter(role -> role.isActive())
                .flatMap(role -> {
                    Set<GrantedAuthority> grants = role.getRolePermissions().stream()
                            .filter(rp -> rp.isActive())
                            .map(rp -> rp.getPermission())
                            .filter(permission -> permission.isActive())
                            .map(permission -> new SimpleGrantedAuthority(permission.getPermissionCode()))
                            .collect(Collectors.toSet());

                    // Also grant the role itself as ROLE_<NAME>
                    grants.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName().name()));

                    return grants.stream();
                })
                .collect(Collectors.toSet());
    }
}

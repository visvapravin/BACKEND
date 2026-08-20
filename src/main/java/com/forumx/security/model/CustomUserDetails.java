package com.forumx.security.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

    private final User user;
    private final Long activeTenantId;
    private final String activeTenantSlug;
    private final Set<GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this(user, null, null, user.getUserRoles());
    }

    public CustomUserDetails(User user, Long activeTenantId, String activeTenantSlug, Collection<UserRole> activeRoles) {
        this.user = user;
        this.activeTenantId = activeTenantId;
        this.activeTenantSlug = activeTenantSlug;
        this.authorities = Collections.unmodifiableSet(buildAuthorities(activeRoles));
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

    // ── Accessors ───────────────────────────────────────────────────────

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    public Long getTenantId() {
        return activeTenantId;
    }

    public String getTenantSlug() {
        return activeTenantSlug;
    }

    public String getScope() {
        return activeTenantId == null ? "PLATFORM" : "TENANT";
    }

    // ── Authority builder ───────────────────────────────────────────────

    private Set<GrantedAuthority> buildAuthorities(Collection<UserRole> rolesToUse) {
        if (rolesToUse == null) {
            return Set.of();
        }

        return rolesToUse.stream()
                .filter(UserRole::isActive)
                .map(UserRole::getRole)
                .filter(role -> role != null && role.isActive())
                .flatMap(role -> {
                    Set<GrantedAuthority> grants = role.getRolePermissions() != null
                            ? role.getRolePermissions().stream()
                                    .filter(rp -> rp != null && rp.isActive())
                                    .map(rp -> rp.getPermission())
                                    .filter(permission -> permission != null && permission.isActive())
                                    .map(permission -> new SimpleGrantedAuthority(permission.getPermissionCode()))
                                    .collect(Collectors.toSet())
                            : new java.util.HashSet<>();

                    // Also grant the role itself as ROLE_<NAME>
                    if (role.getRoleName() != null) {
                        grants.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName().name()));
                    }

                    return grants.stream();
                })
                .collect(Collectors.toSet());
    }
}

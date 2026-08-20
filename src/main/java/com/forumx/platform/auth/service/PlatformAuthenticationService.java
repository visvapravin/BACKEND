package com.forumx.platform.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.forumx.auth.dto.response.LoginResponse;
import com.forumx.auth.entity.RefreshToken;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.platform.auth.dto.PlatformLoginRequest;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAuthenticationService {

    private final UserRepository users;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;

    @Transactional
    public LoginResponse login(PlatformLoginRequest request, HttpServletRequest servletRequest) {
        User user = users.findPlatformUserForAuthentication(request.getUsernameOrEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())
                || !user.isEnabled()
                || user.isAccountLocked()
                || user.isAccountExpired()
                || user.isCredentialsExpired()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        List<UserRole> platformRoles = userRoleRepository.findActivePlatformRolesByUserId(user.getId());
        if (platformRoles.isEmpty()) {
            throw new BadCredentialsException("User does not have active platform administrator privileges");
        }

        String refresh = UUID.randomUUID().toString();
        refreshTokens.save(RefreshToken.builder()
                .user(user)
                .tenant(null)
                .token(refresh)
                .expiresAt(jwt.calculateRefreshTokenExpiry())
                .ipAddress(servletRequest.getRemoteAddr())
                .userAgent(servletRequest.getHeader("User-Agent"))
                .build());

        CustomUserDetails details = new CustomUserDetails(user, null, null, platformRoles);
        return LoginResponse.builder()
                .accessToken(jwt.generateAccessToken(details))
                .refreshToken(refresh)
                .tokenType("Bearer")
                .expiresAt(Instant.now().plusSeconds(900))
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .tenantId(null)
                .roles(details.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5))
                        .toList())
                .build();
    }
}

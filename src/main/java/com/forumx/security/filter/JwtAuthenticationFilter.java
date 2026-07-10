package com.forumx.security.filter;

import java.io.IOException;
import java.util.Optional;

import com.forumx.security.jwt.JwtClaimsConstants;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.service.CustomUserDetailsService;
import com.forumx.tenant.resolver.TenantResolver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security filter that intercepts incoming HTTP requests, extracts the JWT token from the Authorization header,
 * validates it, checks tenant alignment, and sets the Spring Security Authentication context if valid.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TenantResolver tenantResolver;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    private final RequestMatcher ignoredEndpoints = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/v1/auth/**"),
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/v3/api-docs/**"),
            new AntPathRequestMatcher("/actuator/health")
    );

    /**
     * Constructs the JwtAuthenticationFilter with required security dependencies.
     *
     * @param jwtTokenProvider         token utility provider
     * @param userDetailsService       service to load users
     * @param tenantResolver           multi-tenant context resolver
     * @param authenticationEntryPoint custom entrypoint for handling authentication failures
     */
    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            CustomUserDetailsService userDetailsService,
            TenantResolver tenantResolver,
            @Qualifier("customAuthenticationEntryPoint") AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.tenantResolver = tenantResolver;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * Determines whether the request should skip JWT security filtering.
     * Skips for public, Swagger, and health actuator endpoints.
     *
     * @param request the incoming request
     * @return true if the request matches any of the ignored endpoints; false otherwise
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ignoredEndpoints.matches(request);
    }

    /**
     * Core filter method that extracts, parses, validates the JWT, verifies multi-tenancy,
     * and sets the SecurityContextHolder if validation succeeds.
     *
     * @param request     the HTTP request
     * @param response    the HTTP response
     * @param filterChain the servlet filter chain
     * @throws ServletException in case of servlet-level errors
     * @throws IOException      in case of I/O errors during request processing
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Optional<String> tokenOpt = resolveToken(request);
        if (tokenOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = tokenOpt.get();

        try {
            Claims claims = jwtTokenProvider.extractAllClaims(token);

            String username = claims.getSubject();
            Long tokenTenantId = claims.get(JwtClaimsConstants.TENANT_ID, Long.class);

            Long resolvedTenantId = tenantResolver.resolveTenantId();
            if (resolvedTenantId == null || !resolvedTenantId.equals(tokenTenantId)) {
                log.warn("Tenant mismatch: resolved={}, token={}", resolvedTenantId, tokenTenantId);
                commenceAuthenticationFailure(request, response, new BadCredentialsException("Tenant mismatch or unresolved"));
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtTokenProvider.validateToken(token, userDetails)) {
                Authentication auth = buildAuthentication(userDetails, request);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                commenceAuthenticationFailure(request, response, new BadCredentialsException("Invalid JWT token"));
                return;
            }

        } catch (ExpiredJwtException e) {
            log.debug("Invalid token: Expired JWT", e);
            commenceAuthenticationFailure(request, response, new InsufficientAuthenticationException("JWT token has expired", e));
            return;
        } catch (JwtException e) {
            log.debug("Invalid token: Malformed or signature verification failed", e);
            commenceAuthenticationFailure(request, response, new BadCredentialsException("Invalid JWT token", e));
            return;
        } catch (UsernameNotFoundException e) {
            log.debug("Invalid token: User not found", e);
            commenceAuthenticationFailure(request, response, new UsernameNotFoundException("User not found", e));
            return;
        } catch (AuthenticationException e) {
            log.debug("Invalid token: Authentication exception occurred", e);
            commenceAuthenticationFailure(request, response, e);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the token from the request's Authorization header, validating its format strictly.
     *
     * @param request the incoming HTTP request
     * @return an Optional containing the JWT token if present and valid; empty otherwise
     */
    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ") && header.length() > 7) {
            return Optional.of(header.substring(7).trim());
        }
        return Optional.empty();
    }

    /**
     * Builds a UsernamePasswordAuthenticationToken for the authenticated user and maps request details.
     *
     * @param userDetails user security profile details
     * @param request     the HTTP request context
     * @return populated Authentication object
     */
    private Authentication buildAuthentication(UserDetails userDetails, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authentication;
    }

    /**
     * Clears the current security context authentication details.
     */
    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Handles authentication failure by clearing SecurityContext and delegating to the AuthenticationEntryPoint.
     *
     * @param request  the HTTP request context
     * @param response the HTTP response context
     * @param ex       the authentication exception
     * @throws IOException      in case of response rendering issues
     * @throws ServletException in case of servlet dispatch failures
     */
    private void commenceAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException ex
    ) throws IOException, ServletException {
        clearAuthentication();
        authenticationEntryPoint.commence(request, response, ex);
    }
}

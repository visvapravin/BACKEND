package com.forumx.security.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import com.forumx.security.model.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Provides JSON Web Token (JWT) infrastructure operations including token generation,
 * parsing, extraction, validation, and signature verification.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecretKey signingKey;
    private final JwtParser jwtParser;

    /**
     * Constructs the JwtTokenProvider with properties and a clock.
     * Initializes the secret key and the thread-safe parser instance.
     *
     * @param jwtProperties configured JWT properties
     * @param clock clock instance for checking token expiration
     */
    public JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    /**
     * Generates an access token for the specified user details.
     *
     * @param userDetails the user credentials and attributes
     * @return the generated JWT access token string
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();

        extraClaims.put(JwtClaimsConstants.USERNAME, userDetails.getUsername());
        extraClaims.put(JwtClaimsConstants.TOKEN_TYPE, "access");

        if (userDetails instanceof CustomUserDetails customUserDetails) {
            extraClaims.put(JwtClaimsConstants.USER_ID, customUserDetails.getUserId());
            Long tenantId = customUserDetails.getTenantId();
            extraClaims.put(JwtClaimsConstants.SCOPE, tenantId == null ? "PLATFORM" : "TENANT");
            if (tenantId != null) {
                extraClaims.put(JwtClaimsConstants.TENANT_ID, tenantId);
                extraClaims.put(JwtClaimsConstants.TENANT_SLUG, customUserDetails.getTenantSlug());
            }

            if (customUserDetails.getUser() != null) {
                extraClaims.put(JwtClaimsConstants.EMAIL, customUserDetails.getUser().getEmail());
            }

            Collection<? extends GrantedAuthority> authorities = customUserDetails.getAuthorities();
            List<String> roles = authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .map(auth -> auth.substring(5))
                    .toList();

            List<String> permissions = authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> !auth.startsWith("ROLE_"))
                    .toList();

            extraClaims.put(JwtClaimsConstants.ROLES, roles);
            extraClaims.put(JwtClaimsConstants.PERMISSIONS, permissions);
        }

        String jti = UUID.randomUUID().toString();
        extraClaims.put(JwtClaimsConstants.SESSION_ID, jti);

        Instant now = clock.instant();
        Instant expiration = now.plus(jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .id(jti)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Validates if the token belongs to the user and is not expired or malformed.
     *
     * @param token the JWT string to validate
     * @param userDetails the user reference to compare the username against
     * @return true if the token is valid; false otherwise
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token format: {}", e.getMessage());
        } catch (SignatureException e) {
            log.error("JWT signature verification failed: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        } catch (JwtException e) {
            log.error("JWT validation error: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extracts a specific claim from the token.
     *
     * @param token the JWT string
     * @param claimsResolver claims resolver function
     * @param <T> type of the claim
     * @return the extracted claim
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extracts the username (subject) from the token.
     *
     * @param token the JWT string
     * @return the username
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the user ID from the token.
     *
     * @param token the JWT string
     * @return the user ID
     */
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get(JwtClaimsConstants.USER_ID, Long.class));
    }

    /**
     * Extracts the tenant ID from the token.
     *
     * @param token the JWT string
     * @return the tenant ID
     */
    public Long extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get(JwtClaimsConstants.TENANT_ID, Long.class));
    }

    /**
     * Extracts roles from the token.
     *
     * @param token the JWT string
     * @return list of role names
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get(JwtClaimsConstants.ROLES, List.class));
    }

    /**
     * Extracts permissions from the token.
     *
     * @param token the JWT string
     * @return list of permission codes
     */
    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        return extractClaim(token, claims -> claims.get(JwtClaimsConstants.PERMISSIONS, List.class));
    }

    /**
     * Extracts the expiration date from the token.
     *
     * @param token the JWT string
     * @return the expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts the JWT ID (JTI) from the token.
     *
     * @param token the JWT string
     * @return the JWT ID
     */
    public String extractJwtId(String token) {
        return extractClaim(token, Claims::getId);
    }

    /**
     * Extracts the issued at timestamp from the token.
     *
     * @param token the JWT string
     * @return the issued at date
     */
    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    /**
     * Parses the JWT token to extract all claims. This operation verifies the signature
     * and the expiration of the token.
     *
     * @param token the JWT string
     * @return the verified Claims object
     * @throws io.jsonwebtoken.JwtException if the token signature is invalid, expired, or malformed
     */
    public Claims extractAllClaims(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    private boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date(clock.millis()));
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Calculates the refresh token expiration instant based on current clock time.
     *
     * @return the expiration Instant
     */
    public Instant calculateRefreshTokenExpiry() {
        return clock.instant().plus(jwtProperties.getRefreshTokenExpiration());
    }
}

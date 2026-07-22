package com.forumx.websocket.security;

import com.forumx.security.jwt.JwtClaimsConstants;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.service.CustomUserDetailsService;
import com.forumx.websocket.exception.WebSocketAuthenticationException;
import com.forumx.websocket.session.WebSocketSessionContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            if (StringUtils.hasText(token)) {
                try {
                    Claims claims = jwtTokenProvider.extractAllClaims(token);
                    String username = claims.getSubject();
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (jwtTokenProvider.validateToken(token, userDetails) && userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        accessor.setUser(auth);

                        // Extract context claims and store context in session attributes
                        Long tenantId = claims.get(JwtClaimsConstants.TENANT_ID, Long.class);
                        Long userId = claims.get(JwtClaimsConstants.USER_ID, Long.class);
                        @SuppressWarnings("unchecked")
                        List<String> roles = claims.get(JwtClaimsConstants.ROLES, List.class);

                        WebSocketSessionContext context = WebSocketSessionContext.builder()
                                .sessionId(accessor.getSessionId())
                                .userId(userId)
                                .username(username)
                                .tenantId(tenantId)
                                .roles(roles)
                                .build();

                        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                        if (sessionAttributes != null) {
                            sessionAttributes.put(WebSocketSessionContext.SESSION_KEY, context);
                        }
                    } else {
                        throw new WebSocketAuthenticationException("Unauthorized: Invalid token credentials");
                    }
                } catch (JwtException e) {
                    throw new WebSocketAuthenticationException("Unauthorized: Invalid JWT signature or expired: " + e.getMessage());
                } catch (UsernameNotFoundException e) {
                    throw new WebSocketAuthenticationException("Unauthorized: User not found: " + e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error occurred during WebSocket STOMP authentication", e);
                    throw new WebSocketAuthenticationException("Unauthorized: Connection authentication failed: " + e.getMessage());
                }
            } else {
                throw new WebSocketAuthenticationException("Unauthorized: Token missing");
            }
        }
        return message;
    }
}

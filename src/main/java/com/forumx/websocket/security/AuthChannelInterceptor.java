package com.forumx.websocket.security;

import com.forumx.security.jwt.JwtClaimsConstants;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.security.service.CustomUserDetailsService;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
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
    private final SupportSessionParticipantRepository participantRepository;

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
                    Long tenantId = claims.get(JwtClaimsConstants.TENANT_ID, Long.class);
                    Long userId = claims.get(JwtClaimsConstants.USER_ID, Long.class);

                    UserDetails userDetails = userDetailsService.loadUserByTenantIdAndUsername(tenantId, username);

                    if (jwtTokenProvider.validateToken(token, userDetails) && userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        accessor.setUser(auth);

                        // Extract context claims and store context in session attributes
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
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (StringUtils.hasText(destination) && destination.contains("/tenants/")) {
                WebSocketSessionContext context = null;
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null) {
                    context = (WebSocketSessionContext) sessionAttributes.get(WebSocketSessionContext.SESSION_KEY);
                }
                if (context == null && accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof CustomUserDetails details) {
                    context = WebSocketSessionContext.builder()
                            .sessionId(accessor.getSessionId())
                            .userId(details.getUserId())
                            .username(details.getUsername())
                            .tenantId(details.getTenantId())
                            .build();
                    if (sessionAttributes != null) {
                        sessionAttributes.put(WebSocketSessionContext.SESSION_KEY, context);
                    }
                }
                if (context == null) {
                    throw new WebSocketAuthenticationException("Unauthorized: Unauthenticated WebSocket context");
                }
                Long destTenantId = extractTenantIdFromDestination(destination);
                if (destTenantId != null && !destTenantId.equals(context.getTenantId())) {
                    log.warn("STOMP subscription cross-tenant violation. user={}, contextTenantId={}, destTenantId={}, destination={}",
                            context.getUsername(), context.getTenantId(), destTenantId, destination);
                    throw new WebSocketAuthenticationException("Unauthorized: Cross-tenant STOMP subscription rejected");
                }

                // If subscribing to a private chat session topic, enforce active participant authorization
                Long sessionId = extractSessionIdFromDestination(destination);
                if (sessionId != null && destination.contains("/chat/")) {
                    boolean isActive = participantRepository.findBySession_IdAndUser_IdAndIsActiveTrue(sessionId, context.getUserId()).isPresent();
                    if (!isActive) {
                        log.warn("STOMP subscription active participant violation. user={}, userId={}, sessionId={}, destination={}",
                                context.getUsername(), context.getUserId(), sessionId, destination);
                        throw new WebSocketAuthenticationException("Unauthorized: User is not an active participant in this support room");
                    }
                }
            }
        }
        return message;
    }

    private Long extractTenantIdFromDestination(String destination) {
        try {
            int idx = destination.indexOf("/tenants/");
            if (idx != -1) {
                String sub = destination.substring(idx + "/tenants/".length());
                int slash = sub.indexOf('/');
                String idStr = slash != -1 ? sub.substring(0, slash) : sub;
                return Long.parseLong(idStr);
            }
        } catch (Exception e) {
            log.debug("Could not parse tenantId from STOMP destination: {}", destination);
        }
        return null;
    }

    private Long extractSessionIdFromDestination(String destination) {
        try {
            int idx = destination.indexOf("/chat/");
            if (idx != -1) {
                String sub = destination.substring(idx + "/chat/".length());
                int slash = sub.indexOf('/');
                String idStr = slash != -1 ? sub.substring(0, slash) : sub;
                return Long.parseLong(idStr);
            }
        } catch (Exception e) {
            log.debug("Could not parse sessionId from STOMP destination: {}", destination);
        }
        return null;
    }
}

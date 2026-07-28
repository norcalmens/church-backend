package com.norcalretreat.backend.config;

import com.norcalretreat.backend.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * STOMP over WebSocket.
 *
 * Two destination prefixes:
 *   /topic/public/**  -- open, no auth (home hero capacity counter)
 *   /topic/admin/**   -- gated on JWT during STOMP CONNECT (activity feed)
 *
 * We reuse the existing HTTP JWT rather than issue a WebSocket-specific
 * credential -- the frontend passes its access token in the CONNECT frame
 * headers, we parse it the same way {@code JwtAuthenticationFilter} does,
 * and stamp the resolved {@code Principal} onto the session. Subscriptions
 * to {@code /topic/admin/**} are then rejected for anonymous sessions.
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker -- fine for a single-instance deploy.
        // If we ever scale horizontally, swap in RabbitMQ/Redis broker relay.
        config.enableSimpleBroker("/topic");
        // Not currently used (we only broadcast server -> clients), but keep
        // the prefix reserved so future client->server messages have a home.
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket only -- modern browsers all support it, and
        // SockJS pulls in ~30KB of legacy fallbacks we don't need.
        // setAllowedOriginPatterns covers Railway/Hostinger origin variants.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Token is optional -- anonymous sessions can still see
                    // /topic/public/** but subscribing to /topic/admin/** will
                    // fail below because there's no principal.
                    String bearer = accessor.getFirstNativeHeader("Authorization");
                    if (bearer != null && bearer.startsWith("Bearer ")) {
                        String token = bearer.substring(7);
                        try {
                            if (jwtTokenProvider.validateToken(token)) {
                                Claims claims = jwtTokenProvider.getClaimsFromToken(token);
                                String username = claims.getSubject();
                                @SuppressWarnings("unchecked")
                                List<String> roles = (List<String>) claims.getOrDefault("roles", List.of());
                                var authorities = roles.stream()
                                        // Match the exact prefix used by JwtAuthenticationFilter so
                                        // hasAnyRole('ADMIN','SUPERADMIN') checks below match up.
                                        .map(r -> new SimpleGrantedAuthority(
                                                r.startsWith("ROLE_") ? r : "ROLE_" + r))
                                        .collect(Collectors.toList());
                                UsernamePasswordAuthenticationToken auth =
                                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                                accessor.setUser(auth);
                            }
                        } catch (Exception e) {
                            log.warn("STOMP CONNECT with invalid JWT: {}", e.getMessage());
                            // Fall through anonymous -- still get the public topics.
                        }
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String dest = Objects.toString(accessor.getDestination(), "");
                    if (dest.startsWith("/topic/admin/")) {
                        var user = accessor.getUser();
                        boolean isAdmin = user instanceof UsernamePasswordAuthenticationToken auth
                                && auth.getAuthorities().stream().anyMatch(a ->
                                        "ROLE_ADMIN".equals(a.getAuthority())
                                     || "ROLE_SUPERADMIN".equals(a.getAuthority())
                                     || "ROLE_COMMITTEE".equals(a.getAuthority()));
                        if (!isAdmin) {
                            log.warn("Rejected STOMP SUBSCRIBE to {} from anonymous/non-admin session", dest);
                            throw new SecurityException("Not authorized for " + dest);
                        }
                    }
                }
                return message;
            }
        });
    }
}

package com.flashsale.gateway.filter;

import com.flashsale.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Global filter for JWT authentication.
 *
 * Whitelisted paths (login, register) pass through without token.
 * All other requests must carry a valid Bearer JWT token.
 * On success, userId and role are forwarded to downstream services
 * via X-User-Id and X-User-Role headers.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private final JwtUtil jwtUtil;
    private final List<String> whitelist;

    public JwtAuthGlobalFilter(JwtUtil jwtUtil,
                               @Value("#{'${gateway.auth.whitelist}'.split(',')}") List<String> whitelist) {
        this.jwtUtil = jwtUtil;
        this.whitelist = whitelist;
    }

    @Override
    public int getOrder() {
        return 0; // runs after RateLimitFilter (order=-10)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // 1. Whitelist — skip auth, but still add default headers
        if (isWhitelisted(method, path)) {
            log.debug("Whitelisted {}: {}", method, path);
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", "0")
                    .header("X-User-Role", "GUEST")
                    .header("X-User-Name", "anonymous")
                    .build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }

        // 2. Extract token from Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return unauthorized(exchange, "未登录");
        }

        String token = authHeader.substring(7);

        // 3. Validate token
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            log.warn("Invalid or expired token for path: {}", path);
            return unauthorized(exchange, "Token无效或已过期");
        }

        // 4. Extract userId & role and forward as headers
        Long userId = claims.get("userId", Long.class);
        String role = claims.get("role", String.class);

        log.debug("Auth OK — userId={}, role={}, path={}", userId, role, path);

        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    private boolean isWhitelisted(String method, String path) {
        return whitelist.stream().anyMatch(entry -> {
            if (!entry.contains(":")) {
                // Legacy format (no method prefix) — backward compat, match any method
                return path.startsWith(entry);
            }
            String[] parts = entry.split(":", 2);
            return parts[0].equals(method) && path.startsWith(parts[1]);
        });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null,\"timestamp\":"
                + System.currentTimeMillis() + "}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

package com.flashsale.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Simple Redis-based rate limit filter (token-bucket-ish).
 *
 * Strategy:
 *   Key: rate_limit:ip:{clientIp}
 *   TTL: 1 second
 *   Each request increments the counter.
 *   If counter > capacity → 429 Too Many Requests.
 *
 * Seckill paths (/api/seckill/**) use a higher capacity threshold.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int capacity;
    private final int refillRate;
    private final int seckillCapacity;
    private final int seckillRefillRate;

    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate,
                           @Value("${gateway.rate-limit.capacity}") int capacity,
                           @Value("${gateway.rate-limit.refill-rate}") int refillRate,
                           @Value("${gateway.rate-limit.seckill-capacity}") int seckillCapacity,
                           @Value("${gateway.rate-limit.seckill-refill-rate}") int seckillRefillRate) {
        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.seckillCapacity = seckillCapacity;
        this.seckillRefillRate = seckillRefillRate;
    }

    @Override
    public int getOrder() {
        return -10; // runs first
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String clientIp = getClientIp(exchange);

        boolean isSeckill = path.startsWith("/api/seckill/");
        int cap = isSeckill ? seckillCapacity : capacity;
        int refill = isSeckill ? seckillRefillRate : refillRate;

        String redisKey = "rate_limit:ip:" + clientIp;

        // Atomic: INCR + EXPIRE (Lua-style via two calls is fine for this simple use)
        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    // Set expiry on first increment
                    if (count == 1) {
                        return redisTemplate.expire(redisKey, Duration.ofSeconds(1))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > cap) {
                        log.warn("Rate limit exceeded — ip={}, path={}, count={}", clientIp, path, count);
                        return tooManyRequests(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String remoteAddr = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        return remoteAddr;
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        String body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null,\"timestamp\":"
                + System.currentTimeMillis() + "}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

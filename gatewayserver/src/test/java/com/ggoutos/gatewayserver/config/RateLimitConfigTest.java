package com.ggoutos.gatewayserver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitConfig Tests")
class RateLimitConfigTest {

    private final RateLimitConfig rateLimitConfig = new RateLimitConfig();

    @Test
    @DisplayName("Should create Redis rate limiter")
    void redisRateLimiter_shouldCreateLimiter() {
        RedisRateLimiter redisRateLimiter = rateLimitConfig.redisRateLimiter();

        assertThat(redisRateLimiter).isNotNull();
    }

    @Test
    @DisplayName("Should resolve key from authenticated principal")
    void userKeyResolver_shouldUsePrincipalName() {
        KeyResolver keyResolver = rateLimitConfig.userKeyResolver();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build())
                .mutate()
                .principal(Mono.just((Principal) () -> "alice"))
                .build();

        assertThat(keyResolver.resolve(exchange).block()).isEqualTo("alice");
    }

    @Test
    @DisplayName("Should fall back to forwarded client IP")
    void userKeyResolver_shouldUseForwardedFor_whenPrincipalMissing() {
        KeyResolver keyResolver = rateLimitConfig.userKeyResolver();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
                .header("X-Forwarded-For", "203.0.113.10, 198.51.100.20")
                .build());

        assertThat(keyResolver.resolve(exchange).block()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("Should fall back to remote address")
    void userKeyResolver_shouldUseRemoteAddress_whenForwardedForMissing() {
        KeyResolver keyResolver = rateLimitConfig.userKeyResolver();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress("198.51.100.30", 12345))
                .build());

        assertThat(keyResolver.resolve(exchange).block()).isEqualTo("198.51.100.30");
    }

    @Test
    @DisplayName("Should use anonymous key when no client identity is available")
    void userKeyResolver_shouldUseAnonymous_whenNoIdentityAvailable() {
        KeyResolver keyResolver = rateLimitConfig.userKeyResolver();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        assertThat(keyResolver.resolve(exchange).block()).isEqualTo("anonymous");
    }

}

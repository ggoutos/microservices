package com.ggoutos.gatewayserver.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.CONNECT_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

@Configuration
@RequiredArgsConstructor
public class RouteConfig {

    public static final String DNS_PREFIX = "goutos/bank";

    private final KeyResolver userKeyResolver;
    private final RedisRateLimiter redisRateLimiter;

    /**
     * Configures and builds the route locator for the gateway server.
     * The method defines routing configurations for different services including
     * "accounts", "loans", and "cards".
     *
     * @param routeLocatorBuilder the builder used to configure and create route mappings
     * @return the constructed {@link RouteLocator} containing the defined routes
     */
    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder routeLocatorBuilder) {
        return routeLocatorBuilder.routes()
                .route(createRoute("accounts"))
                .route(createRoute("loans"))
                .route(createRoute("cards"))
                .build();
    }

    /**
     * Creates a function to define a route for the specified service in the gateway server.
     * The route is defined with a path pattern, rewrite filters, and a load-balanced URI so that
     * /PREFIX/service/api/** is routed to the corresponding service registered in the service discovery (e.g., Eureka)
     * with the name in uppercase after the PREFIX is stripped out.
     * example.
     * Incoming: /ggoutos/accounts/api/v1/fetch
     * ↓  Path matches /ggoutos/accounts/**
     * ↓  Rewrite strips prefix → /api/v1/fetch
     * ↓  Forward to lb://ACCOUNTS (load-balanced)
     * Upstream microservice receives: /api/v1/fetch
     *
     * @param service the name of the service for which the route is created
     * @return a {@link Function} that takes a {@link PredicateSpec} and returns a {@link Buildable} route
     */
    private Function<PredicateSpec, Buildable<Route>> createRoute(String service) {
        return p -> p
                .path("/" + DNS_PREFIX + "/" + service.toLowerCase() + "/**")
                .filters(f -> f.rewritePath("/" + DNS_PREFIX + "/" + service.toLowerCase() + "/(?<segment>.*)", "/${segment}")
                        .addResponseHeader("X-Response-Time", Instant.now().toString())
                        // this is the circuit breaker for the service that overrides the default circuit breaker + httpclient timeouts configuration defined in the application.yml
                        .circuitBreaker(config -> config.setName(service + "CircuitBreaker").setFallbackUri("forward:/contactSupport"))
                        // this is the retry policy for the service that overrides the default retry policy defined in the application.yml
                        .retry(config -> config.setRetries(3)
                                .setMethods(HttpMethod.GET)
                                .setBackoff(Duration.ofMillis(100), Duration.ofMillis(1000), 2, true))
                        .requestRateLimiter(config -> config.setRateLimiter(redisRateLimiter).setKeyResolver(userKeyResolver))
                )
                .metadata(CONNECT_TIMEOUT_ATTR, 1000)
                .metadata(RESPONSE_TIMEOUT_ATTR, 1000)
                .uri("lb://" + service.toUpperCase());
    }


    /**
     * Alternate to yml configuration. Provides a default customizer for configuring the {@link ReactiveResilience4JCircuitBreakerFactory}.
     * This method sets the default configuration for circuit breakers and time limiters using
     * Resilience4J's configuration builders.
     *
     * @return a {@link Customizer} for {@link ReactiveResilience4JCircuitBreakerFactory} that applies
     * a default configuration with a Resilience4J circuit breaker and time limiter.
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(4)).build())
                .build());
    }

}

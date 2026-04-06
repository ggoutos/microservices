package com.ggoutos.gatewayserver.config;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.function.Function;

@Configuration
public class RouteConfig {

    public static final String DNS_PREFIX = "goutos/bank";

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
                        .addResponseHeader("X-Respose-Time", Instant.now().toString())
                        .circuitBreaker(config -> config.setName(service + "CircuitBreaker").setFallbackUri("forward:/contactSupport"))
                )
                .uri("lb://" + service.toUpperCase());
    }

}

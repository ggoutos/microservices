package com.ggoutos.gatewayserver.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestTraceFilter Tests")
class RequestTraceFilterTest {

    private RequestTraceFilter requestTraceFilter;

    @Mock
    private FilterUtility filterUtility;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        requestTraceFilter = new RequestTraceFilter(filterUtility);
    }

    @Nested
    @DisplayName("filter() Tests")
    class FilterTests {

        @Test
        @DisplayName("Should use existing correlation ID when present")
        void filter_shouldUseExistingCorrelationId() {
            // Given
            String correlationId = UUID.randomUUID().toString();
            MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                    .header(FilterUtility.CORRELATION_ID, correlationId)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterUtility.getCorrelationId(any(HttpHeaders.class))).thenReturn(correlationId);
            when(chain.filter(any())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = requestTraceFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(filterUtility, never()).setCorrelationId(any(), any());
            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("Should generate correlation ID when not present")
        void filter_shouldGenerateCorrelationId_whenNotPresent() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            String generatedCorrelationId = UUID.randomUUID().toString();

            when(filterUtility.getCorrelationId(any(HttpHeaders.class))).thenReturn(null);
            when(filterUtility.setCorrelationId(any(), anyString())).thenAnswer(invocation -> {
                ServerWebExchange ex = invocation.getArgument(0);
                return ex.mutate().build();
            });
            when(chain.filter(any())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = requestTraceFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(filterUtility).setCorrelationId(any(), matches("[0-9a-f-]{36}"));
            verify(chain).filter(any());
        }
    }
}

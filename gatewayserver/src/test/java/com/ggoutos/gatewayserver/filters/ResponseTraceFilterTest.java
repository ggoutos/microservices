package com.ggoutos.gatewayserver.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResponseTraceFilter Tests")
class ResponseTraceFilterTest {

    private ResponseTraceFilter responseTraceFilter;

    @Mock
    private FilterUtility filterUtility;

    @BeforeEach
    void setUp() {
        responseTraceFilter = new ResponseTraceFilter(filterUtility);
    }

    @Test
    @DisplayName("Should create GlobalFilter bean")
    void postGlobalFilter_shouldCreateGlobalFilter() {
        // When
        GlobalFilter filter = responseTraceFilter.postGlobalFilter();

        // Then
        assertThat(filter).isNotNull();
    }

    @Test
    @DisplayName("Should add correlation ID to response when absent")
    void postGlobalFilter_shouldAddCorrelationId_whenResponseHeaderAbsent() {
        when(filterUtility.getCorrelationId(any(HttpHeaders.class))).thenReturn("correlation-id");
        GlobalFilter filter = responseTraceFilter.postGlobalFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
        GatewayFilterChain chain = serverWebExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(FilterUtility.CORRELATION_ID))
                .isEqualTo("correlation-id");
    }

    @Test
    @DisplayName("Should keep existing response correlation ID")
    void postGlobalFilter_shouldKeepExistingCorrelationId() {
        when(filterUtility.getCorrelationId(any(HttpHeaders.class))).thenReturn("new-correlation-id");
        GlobalFilter filter = responseTraceFilter.postGlobalFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
        exchange.getResponse().getHeaders().add(FilterUtility.CORRELATION_ID, "existing-correlation-id");
        GatewayFilterChain chain = serverWebExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getValuesAsList(FilterUtility.CORRELATION_ID))
                .containsExactly("existing-correlation-id");
    }
}

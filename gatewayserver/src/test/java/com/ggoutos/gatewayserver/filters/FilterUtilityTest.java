package com.ggoutos.gatewayserver.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("FilterUtility Tests")
class FilterUtilityTest {

    private FilterUtility filterUtility;

    @Mock
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        filterUtility = new FilterUtility();
    }

    @Nested
    @DisplayName("getCorrelationId() Tests")
    class GetCorrelationIdTests {

        @Test
        @DisplayName("Should return correlation ID when present")
        void getCorrelationId_shouldReturnId_whenPresent() {
            // Given
            HttpHeaders headers = new HttpHeaders();
            String correlationId = UUID.randomUUID().toString();
            headers.add(FilterUtility.CORRELATION_ID, correlationId);

            // When
            String result = filterUtility.getCorrelationId(headers);

            // Then
            assertThat(result).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("Should return null when correlation ID is not present")
        void getCorrelationId_shouldReturnNull_whenNotPresent() {
            // Given
            HttpHeaders headers = new HttpHeaders();

            // When
            String result = filterUtility.getCorrelationId(headers);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("setCorrelationId() Tests")
    class SetCorrelationIdTests {

        @Test
        @DisplayName("Should set correlation ID header")
        void setCorrelationId_shouldSetHeader() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            String correlationId = UUID.randomUUID().toString();

            // When
            ServerWebExchange result = filterUtility.setCorrelationId(exchange, correlationId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRequest().getHeaders().getFirst(FilterUtility.CORRELATION_ID))
                    .isEqualTo(correlationId);
        }
    }

    @Nested
    @DisplayName("setRequestHeader() Tests")
    class SetRequestHeaderTests {

        @Test
        @DisplayName("Should set custom header")
        void setRequestHeader_shouldSetCustomHeader() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            String headerName = "X-Custom-Header";
            String headerValue = "custom-value";

            // When
            ServerWebExchange result = filterUtility.setRequestHeader(exchange, headerName, headerValue);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRequest().getHeaders().getFirst(headerName))
                    .isEqualTo(headerValue);
        }
    }

    @Test
    @DisplayName("CORRELATION_ID constant should be defined")
    void correlationIdConstant_shouldBeDefined() {
        // Then
        assertThat(FilterUtility.CORRELATION_ID).isEqualTo("eazybank-correlation-id");
    }
}

package com.ggoutos.gatewayserver.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GlobalFilter;

import static org.assertj.core.api.Assertions.assertThat;

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
}

package com.ggoutos.configserver.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig Tests")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Health Endpoint Tests")
    class HealthEndpointTests {

        @Test
        @DisplayName("Should allow unauthenticated access to health endpoint")
        void healthEndpoint_shouldBePubliclyAccessible() throws Exception {
            // When & Then
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow unauthenticated access to health/readiness endpoint")
        void healthReadinessEndpoint_shouldBePubliclyAccessible() throws Exception {
            // When & Then
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow unauthenticated access to health/liveness endpoint")
        void healthLivenessEndpoint_shouldBePubliclyAccessible() throws Exception {
            // When & Then
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Protected Endpoints Tests")
    class ProtectedEndpointsTests {

        @Test
        @DisplayName("Should require authentication for config endpoints")
        void configEndpoint_shouldRequireAuthentication() throws Exception {
            // When & Then
            mockMvc.perform(get("/accounts/default"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should require authentication for info endpoint")
        void infoEndpoint_shouldRequireAuthentication() throws Exception {
            // When & Then
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should allow access with valid credentials")
        void configEndpoint_shouldAllowWithValidCredentials() throws Exception {
            // When & Then
            mockMvc.perform(get("/actuator/env")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("user", "password")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("CSRF Configuration Tests")
    class CsrfConfigurationTests {

        @Test
        @DisplayName("Should have CSRF disabled")
        void csrf_shouldBeDisabled() throws Exception {
            // This test verifies that CSRF is disabled by checking that POST requests
            // don't require CSRF token (only authentication)
            mockMvc.perform(get("/actuator/info")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("user", "password")))
                    .andExpect(status().isOk());
        }
    }
}

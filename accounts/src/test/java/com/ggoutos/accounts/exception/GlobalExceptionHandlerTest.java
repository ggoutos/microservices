package com.ggoutos.accounts.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggoutos.accounts.controller.AccountsController;
import com.ggoutos.accounts.service.IAccountsService;
import com.ggoutos.utils.dto.CustomerDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AccountsController.class, excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IAccountsService accountsService;

    @Nested
    @DisplayName("ResourceNotFoundException Tests")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("Should return 404 when ResourceNotFoundException is thrown")
        void handleResourceNotFoundException_shouldReturn404() throws Exception {
            // Given
            when(accountsService.fetchAccount("0000000000"))
                    .thenThrow(new ResourceNotFoundException("Customer", "mobileNumber", "0000000000"));

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "0000000000"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("404 NOT_FOUND"))
                    .andExpect(jsonPath("$.errorMessage").value("Customer not found with the given input data mobileNumber : '0000000000'"))
                    .andExpect(jsonPath("$.apiPath").value("uri=/api/fetch"));
        }
    }

    @Nested
    @DisplayName("CustomerAlreadyExistsException Tests")
    class CustomerAlreadyExistsTests {

        @Test
        @DisplayName("Should return 400 when CustomerAlreadyExistsException is thrown")
        void handleCustomerAlreadyExistsException_shouldReturn400() throws Exception {
            // Given
            CustomerDto customerDto = new CustomerDto();
            customerDto.setName("John Doe");
            customerDto.setEmail("john@example.com");
            customerDto.setMobileNumber("9939321212");

            doThrow(new CustomerAlreadyExistsException("Customer already registered with given mobileNumber 9939321212"))
                    .when(accountsService).createAccount(customerDto);

            // When & Then
            mockMvc.perform(post("/api/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(customerDto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {

        @Test
        @DisplayName("Should return 400 with field errors when validation fails")
        void handleValidationErrors_shouldReturn400WithFieldErrors() throws Exception {
            // Given
            CustomerDto customerDto = new CustomerDto();
            customerDto.setName("Jo"); // Invalid: too short
            customerDto.setEmail("invalid-email"); // Invalid: not an email
            customerDto.setMobileNumber("123"); // Invalid: not 10 digits

            // When & Then
            mockMvc.perform(post("/api/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(customerDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").exists())
                    .andExpect(jsonPath("$.email").exists())
                    .andExpect(jsonPath("$.mobileNumber").exists());
        }
    }

    @Nested
    @DisplayName("Generic Exception Tests")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should return 500 when generic exception is thrown")
        void handleGenericException_shouldReturn500() throws Exception {
            // Given
            when(accountsService.fetchAccount(anyString()))
                    .thenThrow(new RuntimeException("Unexpected database error"));

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "9939321212"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("500 INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.errorMessage").value("Unexpected database error"))
                    .andExpect(jsonPath("$.apiPath").value("uri=/api/fetch"));
        }
    }
}

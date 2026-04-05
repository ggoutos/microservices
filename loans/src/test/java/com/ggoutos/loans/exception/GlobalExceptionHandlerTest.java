package com.ggoutos.loans.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggoutos.loans.controller.LoansController;
import com.ggoutos.loans.service.ILoansService;
import com.ggoutos.utils.dto.LoansDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoansController.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ILoansService loansService;

    @Nested
    @DisplayName("ResourceNotFoundException Tests")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("Should return 404 when ResourceNotFoundException is thrown")
        void handleResourceNotFoundException_shouldReturn404() throws Exception {
            // Given
            when(loansService.fetchLoan("0000000000"))
                    .thenThrow(new ResourceNotFoundException("Loan", "mobileNumber", "0000000000"));

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "0000000000")
                            .header("eazybank-correlation-id", "test-id"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("404 NOT_FOUND"))
                    .andExpect(jsonPath("$.errorMessage").value("Loan not found with the given input data mobileNumber : '0000000000'"));
        }
    }

    @Nested
    @DisplayName("LoanAlreadyExistsException Tests")
    class LoanAlreadyExistsTests {

        @Test
        @DisplayName("Should return 400 when LoanAlreadyExistsException is thrown")
        void handleLoanAlreadyExistsException_shouldReturn400() throws Exception {
            // Given
            doThrow(new LoanAlreadyExistsException("Loan already registered with given mobileNumber 9939321212"))
                    .when(loansService).createLoan("9939321212");

            // When & Then
            mockMvc.perform(post("/api/create")
                            .param("mobileNumber", "9939321212"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {

        @Test
        @DisplayName("Should return 400 when validation fails")
        void handleValidationErrors_shouldReturn400() throws Exception {
            // Given
            LoansDto loansDto = new LoansDto();
            loansDto.setLoanNumber("123"); // Invalid

            // When & Then
            mockMvc.perform(put("/api/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loansDto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Generic Exception Tests")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should return 500 when generic exception is thrown")
        void handleGenericException_shouldReturn500() throws Exception {
            // Given
            when(loansService.fetchLoan(anyString()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "9939321212")
                            .header("eazybank-correlation-id", "test-id"))
                    .andExpect(status().isInternalServerError());
        }
    }
}

package com.ggoutos.loans.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggoutos.loans.constants.LoansConstants;
import com.ggoutos.loans.service.ILoansService;
import com.ggoutos.utils.dto.LoansDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoansController.class)
@DisplayName("LoansController Tests")
class LoansControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ILoansService loansService;

    private LoansDto testLoansDto;
    private static final String TEST_MOBILE_NUMBER = "9939321212";
    private static final String TEST_CORRELATION_ID = "test-correlation-id";

    @BeforeEach
    void setUp() {
        testLoansDto = new LoansDto();
        testLoansDto.setMobileNumber(TEST_MOBILE_NUMBER);
        testLoansDto.setLoanNumber("100123456789");
        testLoansDto.setLoanType("Home Loan");
        testLoansDto.setTotalLoan(100000);
        testLoansDto.setAmountPaid(10000);
        testLoansDto.setOutstandingAmount(90000);
    }

    @Nested
    @DisplayName("createLoan() Tests")
    class CreateLoanTests {

        @Test
        @DisplayName("Should create loan and return 201")
        void createLoan_shouldReturn201() throws Exception {
            // Given
            doNothing().when(loansService).createLoan(TEST_MOBILE_NUMBER);

            // When & Then
            mockMvc.perform(post("/api/create")
                            .param("mobileNumber", TEST_MOBILE_NUMBER))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_201))
                    .andExpect(jsonPath("$.statusMsg").value(LoansConstants.MESSAGE_201));

            verify(loansService).createLoan(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should return 400 when mobile number is invalid")
        void createLoan_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/create")
                            .param("mobileNumber", "123"))
                    .andExpect(status().isBadRequest());

            verify(loansService, never()).createLoan(anyString());
        }
    }

    @Nested
    @DisplayName("fetchLoanDetails() Tests")
    class FetchLoanTests {

        @Test
        @DisplayName("Should fetch loan and return 200")
        void fetchLoanDetails_shouldReturn200() throws Exception {
            // Given
            when(loansService.fetchLoan(TEST_MOBILE_NUMBER)).thenReturn(testLoansDto);

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", TEST_MOBILE_NUMBER)
                            .header("eazybank-correlation-id", TEST_CORRELATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mobileNumber").value(TEST_MOBILE_NUMBER))
                    .andExpect(jsonPath("$.loanNumber").value(testLoansDto.getLoanNumber()));

            verify(loansService).fetchLoan(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should return 400 when correlation-id header is missing")
        void fetchLoanDetails_shouldReturn400_whenCorrelationIdMissing() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", TEST_MOBILE_NUMBER))
                    .andExpect(status().isBadRequest());

            verify(loansService, never()).fetchLoan(anyString());
        }

        @Test
        @DisplayName("Should return 400 when mobile number is invalid")
        void fetchLoanDetails_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "123")
                            .header("eazybank-correlation-id", TEST_CORRELATION_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("updateLoanDetails() Tests")
    class UpdateLoanTests {

        @Test
        @DisplayName("Should update loan and return 200")
        void updateLoanDetails_shouldReturn200() throws Exception {
            // Given
            when(loansService.updateLoan(any(LoansDto.class))).thenReturn(true);

            // When & Then
            mockMvc.perform(put("/api/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testLoansDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_200))
                    .andExpect(jsonPath("$.statusMsg").value(LoansConstants.MESSAGE_200));

            verify(loansService).updateLoan(any(LoansDto.class));
        }

        @Test
        @DisplayName("Should return 417 when update fails")
        void updateLoanDetails_shouldReturn417_whenUpdateFails() throws Exception {
            // Given
            when(loansService.updateLoan(any(LoansDto.class))).thenReturn(false);

            // When & Then
            mockMvc.perform(put("/api/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testLoansDto)))
                    .andExpect(status().isExpectationFailed())
                    .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_417));
        }
    }

    @Nested
    @DisplayName("deleteLoanDetails() Tests")
    class DeleteLoanTests {

        @Test
        @DisplayName("Should delete loan and return 200")
        void deleteLoanDetails_shouldReturn200() throws Exception {
            // Given
            when(loansService.deleteLoan(TEST_MOBILE_NUMBER)).thenReturn(true);

            // When & Then
            mockMvc.perform(delete("/api/delete")
                            .param("mobileNumber", TEST_MOBILE_NUMBER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_200))
                    .andExpect(jsonPath("$.statusMsg").value(LoansConstants.MESSAGE_200));

            verify(loansService).deleteLoan(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should return 400 when mobile number is invalid")
        void deleteLoanDetails_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/delete")
                            .param("mobileNumber", "123"))
                    .andExpect(status().isBadRequest());

            verify(loansService, never()).deleteLoan(anyString());
        }
    }
}

package com.ggoutos.accounts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggoutos.accounts.constants.AccountsConstants;
import com.ggoutos.accounts.service.IAccountsService;
import com.ggoutos.utils.dto.AccountsDto;
import com.ggoutos.utils.dto.CustomerDto;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountsController.class)
@DisplayName("AccountsController Tests")
class AccountsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IAccountsService accountsService;

    private CustomerDto testCustomerDto;

    @BeforeEach
    void setUp() {
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountNumber(1234567890L);
        accountsDto.setAccountType(AccountsConstants.SAVINGS);
        accountsDto.setBranchAddress(AccountsConstants.ADDRESS);

        testCustomerDto = new CustomerDto();
        testCustomerDto.setName("John Doe");
        testCustomerDto.setEmail("john@example.com");
        testCustomerDto.setMobileNumber("9939321212");
        testCustomerDto.setAccountsDto(accountsDto);
    }

    @Nested
    @DisplayName("createAccount() Tests")
    class CreateAccountTests {

        @Test
        @DisplayName("Should create account and return 201")
        void createAccount_shouldReturn201() throws Exception {
            // Given
            doNothing().when(accountsService).createAccount(any(CustomerDto.class));

            // When & Then
            mockMvc.perform(post("/api/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testCustomerDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_201))
                    .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_201));

            verify(accountsService).createAccount(any(CustomerDto.class));
        }

        @Test
        @DisplayName("Should return 400 when mobile number is invalid")
        void createAccount_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // Given
            testCustomerDto.setMobileNumber("123"); // Invalid

            // When & Then
            mockMvc.perform(post("/api/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testCustomerDto)))
                    .andExpect(status().isBadRequest());

            verify(accountsService, never()).createAccount(any(CustomerDto.class));
        }

        @Test
        @DisplayName("Should return 400 when name is invalid")
        void createAccount_shouldReturn400_whenNameInvalid() throws Exception {
            // Given
            testCustomerDto.setName("Jo"); // Too short (less than 5 chars)

            // When & Then
            mockMvc.perform(post("/api/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testCustomerDto)))
                    .andExpect(status().isBadRequest());

            verify(accountsService, never()).createAccount(any(CustomerDto.class));
        }
    }

    @Nested
    @DisplayName("fetchAccountDetails() Tests")
    class FetchAccountTests {

        @Test
        @DisplayName("Should fetch account and return 200")
        void fetchAccountDetails_shouldReturn200() throws Exception {
            // Given
            when(accountsService.fetchAccount(testCustomerDto.getMobileNumber()))
                    .thenReturn(testCustomerDto);

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", testCustomerDto.getMobileNumber()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(testCustomerDto.getName()))
                    .andExpect(jsonPath("$.email").value(testCustomerDto.getEmail()))
                    .andExpect(jsonPath("$.mobileNumber").value(testCustomerDto.getMobileNumber()));

            verify(accountsService).fetchAccount(testCustomerDto.getMobileNumber());
        }

        @Test
        @DisplayName("Should return 400 when mobile number format is invalid")
        void fetchAccountDetails_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", "123"))
                    .andExpect(status().isBadRequest());

            verify(accountsService, never()).fetchAccount(anyString());
        }

        @Test
        @DisplayName("Should return 500 when service throws exception")
        void fetchAccountDetails_shouldReturn500_whenServiceThrowsException() throws Exception {
            // Given
            when(accountsService.fetchAccount(testCustomerDto.getMobileNumber()))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(get("/api/fetch")
                            .param("mobileNumber", testCustomerDto.getMobileNumber()))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("updateAccountDetails() Tests")
    class UpdateAccountTests {

        @Test
        @DisplayName("Should update account and return 200")
        void updateAccountDetails_shouldReturn200() throws Exception {
            // Given
            when(accountsService.updateAccount(any(CustomerDto.class))).thenReturn(true);

            // When & Then
            mockMvc.perform(put("/api/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testCustomerDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_200))
                    .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_200));

            verify(accountsService).updateAccount(any(CustomerDto.class));
        }

        @Test
        @DisplayName("Should return 417 when update fails")
        void updateAccountDetails_shouldReturn417_whenUpdateFails() throws Exception {
            // Given
            when(accountsService.updateAccount(any(CustomerDto.class))).thenReturn(false);

            // When & Then
            mockMvc.perform(put("/api/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testCustomerDto)))
                    .andExpect(status().isExpectationFailed())
                    .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_417))
                    .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_417_UPDATE));
        }
    }

    @Nested
    @DisplayName("deleteAccountDetails() Tests")
    class DeleteAccountTests {

        @Test
        @DisplayName("Should delete account and return 200")
        void deleteAccountDetails_shouldReturn200() throws Exception {
            // Given
            when(accountsService.deleteAccount(testCustomerDto.getMobileNumber())).thenReturn(true);

            // When & Then
            mockMvc.perform(delete("/api/delete")
                            .param("mobileNumber", testCustomerDto.getMobileNumber()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_200))
                    .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_200));

            verify(accountsService).deleteAccount(testCustomerDto.getMobileNumber());
        }

        @Test
        @DisplayName("Should return 417 when delete fails")
        void deleteAccountDetails_shouldReturn417_whenDeleteFails() throws Exception {
            // Given
            when(accountsService.deleteAccount(testCustomerDto.getMobileNumber())).thenReturn(false);

            // When & Then
            mockMvc.perform(delete("/api/delete")
                            .param("mobileNumber", testCustomerDto.getMobileNumber()))
                    .andExpect(status().isExpectationFailed())
                    .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_417))
                    .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_417_DELETE));
        }

        @Test
        @DisplayName("Should return 400 when mobile number format is invalid")
        void deleteAccountDetails_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/delete")
                            .param("mobileNumber", "123"))
                    .andExpect(status().isBadRequest());

            verify(accountsService, never()).deleteAccount(anyString());
        }
    }
}

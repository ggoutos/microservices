package com.ggoutos.accounts.controller;

import com.ggoutos.accounts.service.ICustomersService;
import com.ggoutos.utils.dto.AccountsDto;
import com.ggoutos.utils.dto.CardsDto;
import com.ggoutos.utils.dto.CustomerDetailsDto;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@DisplayName("CustomerController Tests")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ICustomersService customersService;

    private CustomerDetailsDto testCustomerDetailsDto;
    private static final String CORRELATION_ID = "test-correlation-id-12345";
    private static final String MOBILE_NUMBER = "9939321212";

    @BeforeEach
    void setUp() {
        // Setup AccountsDto
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountNumber(1234567890L);
        accountsDto.setAccountType("Savings");
        accountsDto.setBranchAddress("123 Main Street, New York");

        // Setup CardsDto
        CardsDto cardsDto = new CardsDto();
        cardsDto.setMobileNumber(MOBILE_NUMBER);
        cardsDto.setCardNumber("4532123456789012");
        cardsDto.setCardType("Credit Card");
        cardsDto.setTotalLimit(100000);
        cardsDto.setAmountUsed(25000);
        cardsDto.setAvailableAmount(75000);

        // Setup LoansDto
        LoansDto loansDto = new LoansDto();
        loansDto.setMobileNumber(MOBILE_NUMBER);
        loansDto.setLoanNumber("100000000001");
        loansDto.setLoanType("Home Loan");
        loansDto.setTotalLoan(100000);
        loansDto.setAmountPaid(30000);
        loansDto.setOutstandingAmount(70000);

        // Setup CustomerDetailsDto
        testCustomerDetailsDto = new CustomerDetailsDto();
        testCustomerDetailsDto.setName("John Doe");
        testCustomerDetailsDto.setEmail("john@example.com");
        testCustomerDetailsDto.setMobileNumber(MOBILE_NUMBER);
        testCustomerDetailsDto.setAccountsDto(accountsDto);
        testCustomerDetailsDto.setCardsDto(cardsDto);
        testCustomerDetailsDto.setLoansDto(loansDto);
    }

    @Nested
    @DisplayName("fetchCustomerDetails() Tests")
    class FetchCustomerDetailsTests {

        @Test
        @DisplayName("Should fetch customer details and return 200")
        void fetchCustomerDetails_shouldReturn200() throws Exception {
            // Given
            when(customersService.fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID))
                    .thenReturn(testCustomerDetailsDto);

            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", MOBILE_NUMBER)
                            .header("eazybank-correlation-id", CORRELATION_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(testCustomerDetailsDto.getName()))
                    .andExpect(jsonPath("$.email").value(testCustomerDetailsDto.getEmail()))
                    .andExpect(jsonPath("$.mobileNumber").value(testCustomerDetailsDto.getMobileNumber()))
                    .andExpect(jsonPath("$.accountsDto.accountNumber").value(testCustomerDetailsDto.getAccountsDto().getAccountNumber()))
                    .andExpect(jsonPath("$.accountsDto.accountType").value(testCustomerDetailsDto.getAccountsDto().getAccountType()))
                    .andExpect(jsonPath("$.cardsDto.cardNumber").value(testCustomerDetailsDto.getCardsDto().getCardNumber()))
                    .andExpect(jsonPath("$.cardsDto.cardType").value(testCustomerDetailsDto.getCardsDto().getCardType()))
                    .andExpect(jsonPath("$.loansDto.loanNumber").value(testCustomerDetailsDto.getLoansDto().getLoanNumber()))
                    .andExpect(jsonPath("$.loansDto.loanType").value(testCustomerDetailsDto.getLoansDto().getLoanType()));

            verify(customersService).fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID);
        }

        @Test
        @DisplayName("Should return 400 when mobile number is missing")
        void fetchCustomerDetails_shouldReturn400_whenMobileNumberMissing() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isBadRequest());

            verify(customersService, never()).fetchCustomerDetails(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return 400 when mobile number format is invalid (less than 10 digits)")
        void fetchCustomerDetails_shouldReturn400_whenMobileNumberInvalid() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", "12345")
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isBadRequest());

            verify(customersService, never()).fetchCustomerDetails(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return 400 when mobile number contains non-numeric characters")
        void fetchCustomerDetails_shouldReturn400_whenMobileNumberContainsLetters() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", "99393abc12")
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isBadRequest());

            verify(customersService, never()).fetchCustomerDetails(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return 400 when mobile number has more than 10 digits")
        void fetchCustomerDetails_shouldReturn400_whenMobileNumberTooLong() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", "99393212123")
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isBadRequest());

            verify(customersService, never()).fetchCustomerDetails(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return 400 when correlation ID header is missing")
        void fetchCustomerDetails_shouldReturn400_whenCorrelationIdMissing() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", MOBILE_NUMBER))
                    .andExpect(status().isBadRequest());

            verify(customersService, never()).fetchCustomerDetails(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return 500 when service throws exception")
        void fetchCustomerDetails_shouldReturn500_whenServiceThrowsException() throws Exception {
            // Given
            when(customersService.fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", MOBILE_NUMBER)
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isInternalServerError());

            verify(customersService).fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID);
        }

        @Test
        @DisplayName("Should return customer details with only accounts (cards and loans null)")
        void fetchCustomerDetails_shouldReturnPartialData() throws Exception {
            // Given
            CustomerDetailsDto partialDto = new CustomerDetailsDto();
            partialDto.setName("Jane Doe");
            partialDto.setEmail("jane@example.com");
            partialDto.setMobileNumber(MOBILE_NUMBER);
            
            AccountsDto accountsDto = new AccountsDto();
            accountsDto.setAccountNumber(9876543210L);
            accountsDto.setAccountType("Current");
            accountsDto.setBranchAddress("456 Oak Avenue, Chicago");
            partialDto.setAccountsDto(accountsDto);
            // cardsDto and loansDto are null

            when(customersService.fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID))
                    .thenReturn(partialDto);

            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", MOBILE_NUMBER)
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Jane Doe"))
                    .andExpect(jsonPath("$.email").value("jane@example.com"))
                    .andExpect(jsonPath("$.mobileNumber").value(MOBILE_NUMBER))
                    .andExpect(jsonPath("$.accountsDto.accountNumber").value(9876543210L))
                    .andExpect(jsonPath("$.cardsDto").doesNotExist())
                    .andExpect(jsonPath("$.loansDto").doesNotExist());

            verify(customersService).fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID);
        }

        @Test
        @DisplayName("Should handle different correlation IDs")
        void fetchCustomerDetails_shouldHandleDifferentCorrelationIds() throws Exception {
            // Given
            String differentCorrelationId = "different-correlation-id-67890";
            when(customersService.fetchCustomerDetails(MOBILE_NUMBER, differentCorrelationId))
                    .thenReturn(testCustomerDetailsDto);

            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", MOBILE_NUMBER)
                            .header("eazybank-correlation-id", differentCorrelationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mobileNumber").value(MOBILE_NUMBER));

            verify(customersService).fetchCustomerDetails(MOBILE_NUMBER, differentCorrelationId);
        }

        @Test
        @DisplayName("Should return 400 when both mobile number and correlation ID are invalid")
        void fetchCustomerDetails_shouldReturn400_whenBothParamsInvalid() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", "invalid")
                            .header("eazybank-correlation-id", ""))
                    .andExpect(status().isBadRequest());

            verify(customersService, never()).fetchCustomerDetails(anyString(), anyString());
        }

        @Test
        @DisplayName("Should verify response content type is application/json")
        void fetchCustomerDetails_shouldReturnJsonContentType() throws Exception {
            // Given
            when(customersService.fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID))
                    .thenReturn(testCustomerDetailsDto);

            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", MOBILE_NUMBER)
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }
    }
}

package com.ggoutos.accounts.service.impl;

import com.ggoutos.accounts.entity.Accounts;
import com.ggoutos.accounts.entity.Customer;
import com.ggoutos.accounts.exception.ResourceNotFoundException;
import com.ggoutos.accounts.repository.AccountsRepository;
import com.ggoutos.accounts.repository.CustomerRepository;
import com.ggoutos.accounts.service.client.CardsFeignClient;
import com.ggoutos.accounts.service.client.LoansFeignClient;
import com.ggoutos.utils.dto.CardsDto;
import com.ggoutos.utils.dto.CustomerDetailsDto;
import com.ggoutos.utils.dto.LoansDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomersServiceImpl Tests")
class CustomersServiceImplTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CardsFeignClient cardsFeignClient;

    @Mock
    private LoansFeignClient loansFeignClient;

    @InjectMocks
    private CustomersServiceImpl customersService;

    @Nested
    @DisplayName("fetchCustomerDetails")
    class FetchCustomerDetailsTests {

        @Test
        @DisplayName("Should return customer details with cards and loans when customer exists")
        void fetchCustomerDetails_shouldReturnCustomerDetails_whenCustomerExists() {
            // Given
            String mobileNumber = "9876543210";
            String correlationId = "test-correlation-id-123";
            Long customerId = 1L;

            Customer customer = new Customer();
            customer.setCustomerId(customerId);
            customer.setName("John Doe");
            customer.setEmail("john.doe@example.com");
            customer.setMobileNumber(mobileNumber);
            customer.setCreatedBy("ACCOUNTS_MS");
            customer.setCreatedAt(LocalDateTime.now().minusDays(30));

            Accounts accounts = new Accounts();
            accounts.setCustomerId(customerId);
            accounts.setAccountNumber(1000000001L);
            accounts.setAccountType("Savings");
            accounts.setBranchAddress("123 Main Street, New York");
            accounts.setCreatedBy("ACCOUNTS_MS");
            accounts.setCreatedAt(LocalDateTime.now().minusDays(30));

            CardsDto cardsDto = new CardsDto();
            cardsDto.setMobileNumber(mobileNumber);
            cardsDto.setCardNumber("4111111111111111");
            cardsDto.setCardType("Credit Card");
            cardsDto.setTotalLimit(100000);
            cardsDto.setAmountUsed(25000);
            cardsDto.setAvailableAmount(75000);

            LoansDto loansDto = new LoansDto();
            loansDto.setMobileNumber(mobileNumber);
            loansDto.setLoanNumber("100000000001");
            loansDto.setLoanType("Home Loan");
            loansDto.setTotalLoan(500000);
            loansDto.setAmountPaid(150000);
            loansDto.setOutstandingAmount(350000);

            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.of(customer));
            when(accountsRepository.findByCustomerId(customerId))
                    .thenReturn(Optional.of(accounts));
            when(loansFeignClient.fetchLoanDetails(eq(correlationId), eq(mobileNumber)))
                    .thenReturn(loansDto);
            when(cardsFeignClient.fetchCardDetails(eq(correlationId), eq(mobileNumber)))
                    .thenReturn(cardsDto);

            // When
            CustomerDetailsDto result = customersService.fetchCustomerDetails(mobileNumber, correlationId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("John Doe");
            assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
            assertThat(result.getMobileNumber()).isEqualTo(mobileNumber);

            assertThat(result.getAccountsDto()).isNotNull();
            assertThat(result.getAccountsDto().getAccountNumber()).isEqualTo(1000000001L);
            assertThat(result.getAccountsDto().getAccountType()).isEqualTo("Savings");
            assertThat(result.getAccountsDto().getBranchAddress()).isEqualTo("123 Main Street, New York");

            assertThat(result.getCardsDto()).isNotNull();
            assertThat(result.getCardsDto().getCardNumber()).isEqualTo("4111111111111111");
            assertThat(result.getCardsDto().getCardType()).isEqualTo("Credit Card");
            assertThat(result.getCardsDto().getTotalLimit()).isEqualTo(100000);

            assertThat(result.getLoansDto()).isNotNull();
            assertThat(result.getLoansDto().getLoanNumber()).isEqualTo("100000000001");
            assertThat(result.getLoansDto().getLoanType()).isEqualTo("Home Loan");
            assertThat(result.getLoansDto().getTotalLoan()).isEqualTo(500000);

            verify(customerRepository).findByMobileNumber(mobileNumber);
            verify(accountsRepository).findByCustomerId(customerId);
            verify(loansFeignClient).fetchLoanDetails(correlationId, mobileNumber);
            verify(cardsFeignClient).fetchCardDetails(correlationId, mobileNumber);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when customer not found")
        void fetchCustomerDetails_shouldThrowResourceNotFoundException_whenCustomerNotFound() {
            // Given
            String mobileNumber = "0000000000";
            String correlationId = "test-correlation-id-456";

            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> customersService.fetchCustomerDetails(mobileNumber, correlationId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer not found with the given input data mobileNumber : '0000000000'");

            verify(customerRepository).findByMobileNumber(mobileNumber);
            verify(accountsRepository, org.mockito.Mockito.never()).findByCustomerId(any());
            verify(loansFeignClient, org.mockito.Mockito.never()).fetchLoanDetails(any(), any());
            verify(cardsFeignClient, org.mockito.Mockito.never()).fetchCardDetails(any(), any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when account not found for customer")
        void fetchCustomerDetails_shouldThrowResourceNotFoundException_whenAccountNotFound() {
            // Given
            String mobileNumber = "9876543210";
            String correlationId = "test-correlation-id-789";
            Long customerId = 1L;

            Customer customer = new Customer();
            customer.setCustomerId(customerId);
            customer.setName("Jane Smith");
            customer.setEmail("jane.smith@example.com");
            customer.setMobileNumber(mobileNumber);

            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.of(customer));
            when(accountsRepository.findByCustomerId(customerId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> customersService.fetchCustomerDetails(mobileNumber, correlationId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account not found with the given input data customerId : '1'");

            verify(customerRepository).findByMobileNumber(mobileNumber);
            verify(accountsRepository).findByCustomerId(customerId);
            verify(loansFeignClient, org.mockito.Mockito.never()).fetchLoanDetails(any(), any());
            verify(cardsFeignClient, org.mockito.Mockito.never()).fetchCardDetails(any(), any());
        }

        @Test
        @DisplayName("Should pass correlation ID to Feign client calls")
        void fetchCustomerDetails_shouldPassCorrelationId_toFeignClients() {
            // Given
            String mobileNumber = "9876543210";
            String correlationId = "unique-correlation-id-xyz";
            Long customerId = 1L;

            Customer customer = new Customer();
            customer.setCustomerId(customerId);
            customer.setName("Test User");
            customer.setEmail("test@example.com");
            customer.setMobileNumber(mobileNumber);

            Accounts accounts = new Accounts();
            accounts.setCustomerId(customerId);
            accounts.setAccountNumber(1000000002L);
            accounts.setAccountType("Checking");
            accounts.setBranchAddress("456 Oak Avenue");

            CardsDto cardsDto = new CardsDto();
            cardsDto.setMobileNumber(mobileNumber);
            cardsDto.setCardNumber("5500000000000004");

            LoansDto loansDto = new LoansDto();
            loansDto.setMobileNumber(mobileNumber);
            loansDto.setLoanNumber("100000000002");

            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.of(customer));
            when(accountsRepository.findByCustomerId(customerId))
                    .thenReturn(Optional.of(accounts));
            when(loansFeignClient.fetchLoanDetails(eq(correlationId), eq(mobileNumber)))
                    .thenReturn(loansDto);
            when(cardsFeignClient.fetchCardDetails(eq(correlationId), eq(mobileNumber)))
                    .thenReturn(cardsDto);

            // When
            customersService.fetchCustomerDetails(mobileNumber, correlationId);

            // Then
            verify(loansFeignClient).fetchLoanDetails(correlationId, mobileNumber);
            verify(cardsFeignClient).fetchCardDetails(correlationId, mobileNumber);
        }
    }
}
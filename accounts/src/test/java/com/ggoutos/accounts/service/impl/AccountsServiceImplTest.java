package com.ggoutos.accounts.service.impl;

import com.ggoutos.accounts.constants.AccountsConstants;
import com.ggoutos.accounts.entity.Accounts;
import com.ggoutos.accounts.entity.Customer;
import com.ggoutos.accounts.exception.CustomerAlreadyExistsException;
import com.ggoutos.accounts.exception.ResourceNotFoundException;
import com.ggoutos.accounts.repository.AccountsRepository;
import com.ggoutos.accounts.repository.CustomerRepository;
import com.ggoutos.utils.dto.AccountsDto;
import com.ggoutos.utils.dto.CustomerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountsServiceImpl Tests")
class AccountsServiceImplTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private AccountsServiceImpl accountsService;

    private Customer testCustomer;
    private Accounts testAccount;
    private CustomerDto testCustomerDto;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setCustomerId(1L);
        testCustomer.setName("John Doe");
        testCustomer.setEmail("john@example.com");
        testCustomer.setMobileNumber("9939321212");

        testAccount = new Accounts();
        testAccount.setAccountNumber(1234567890L);
        testAccount.setCustomerId(1L);
        testAccount.setAccountType(AccountsConstants.SAVINGS);
        testAccount.setBranchAddress(AccountsConstants.ADDRESS);

        testCustomerDto = new CustomerDto();
        testCustomerDto.setName("John Doe");
        testCustomerDto.setEmail("john@example.com");
        testCustomerDto.setMobileNumber("9939321212");
    }

    @Nested
    @DisplayName("createAccount() Tests")
    class CreateAccountTests {

        @Test
        @DisplayName("Should create customer and account successfully")
        void createAccount_shouldSaveCustomerAndAccount() {
            // Given
            when(customerRepository.findByMobileNumber(testCustomerDto.getMobileNumber()))
                    .thenReturn(Optional.empty());
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);
            when(accountsRepository.save(any(Accounts.class))).thenReturn(testAccount);

            // When
            accountsService.createAccount(testCustomerDto);

            // Then
            verify(customerRepository).findByMobileNumber(testCustomerDto.getMobileNumber());
            verify(customerRepository).save(any(Customer.class));
            verify(accountsRepository).save(any(Accounts.class));
        }

        @Test
        @DisplayName("Should throw CustomerAlreadyExistsException when customer already exists")
        void createAccount_shouldThrowException_whenCustomerAlreadyExists() {
            // Given
            when(customerRepository.findByMobileNumber(testCustomerDto.getMobileNumber()))
                    .thenReturn(Optional.of(testCustomer));

            // When & Then
            assertThatThrownBy(() -> accountsService.createAccount(testCustomerDto))
                    .isInstanceOf(CustomerAlreadyExistsException.class)
                    .hasMessageContaining("Customer already registered with given mobileNumber");

            verify(customerRepository, never()).save(any(Customer.class));
            verify(accountsRepository, never()).save(any(Accounts.class));
        }

        @Test
        @DisplayName("Should generate 10-digit account number")
        void createAccount_shouldGenerateValidAccountNumber() {
            // Given
            when(customerRepository.findByMobileNumber(testCustomerDto.getMobileNumber()))
                    .thenReturn(Optional.empty());
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);
            when(accountsRepository.save(any(Accounts.class))).thenAnswer(invocation -> {
                Accounts savedAccount = invocation.getArgument(0);
                assertThat(savedAccount.getAccountNumber()).isBetween(1000000000L, 1999999999L);
                return savedAccount;
            });

            // When
            accountsService.createAccount(testCustomerDto);

            // Then
            verify(accountsRepository).save(argThat(account ->
                    account.getAccountNumber() >= 1000000000L &&
                    account.getAccountNumber() <= 1999999999L &&
                    AccountsConstants.SAVINGS.equals(account.getAccountType()) &&
                    AccountsConstants.ADDRESS.equals(account.getBranchAddress())
            ));
        }
    }

    @Nested
    @DisplayName("fetchAccount() Tests")
    class FetchAccountTests {

        @Test
        @DisplayName("Should fetch account successfully")
        void fetchAccount_shouldReturnCustomerDto() {
            // Given
            when(customerRepository.findByMobileNumber(testCustomer.getMobileNumber()))
                    .thenReturn(Optional.of(testCustomer));
            when(accountsRepository.findByCustomerId(testCustomer.getCustomerId()))
                    .thenReturn(Optional.of(testAccount));

            // When
            CustomerDto result = accountsService.fetchAccount(testCustomer.getMobileNumber());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(testCustomer.getName());
            assertThat(result.getEmail()).isEqualTo(testCustomer.getEmail());
            assertThat(result.getMobileNumber()).isEqualTo(testCustomer.getMobileNumber());
            assertThat(result.getAccountsDto()).isNotNull();
            assertThat(result.getAccountsDto().getAccountNumber()).isEqualTo(testAccount.getAccountNumber());
            assertThat(result.getAccountsDto().getAccountType()).isEqualTo(testAccount.getAccountType());

            verify(customerRepository).findByMobileNumber(testCustomer.getMobileNumber());
            verify(accountsRepository).findByCustomerId(testCustomer.getCustomerId());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when customer not found")
        void fetchAccount_shouldThrowException_whenCustomerNotFound() {
            // Given
            String mobileNumber = "0000000000";
            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> accountsService.fetchAccount(mobileNumber))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer not found");

            verify(accountsRepository, never()).findByCustomerId(anyLong());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when account not found")
        void fetchAccount_shouldThrowException_whenAccountNotFound() {
            // Given
            when(customerRepository.findByMobileNumber(testCustomer.getMobileNumber()))
                    .thenReturn(Optional.of(testCustomer));
            when(accountsRepository.findByCustomerId(testCustomer.getCustomerId()))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> accountsService.fetchAccount(testCustomer.getMobileNumber()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }
    }

    @Nested
    @DisplayName("updateAccount() Tests")
    class UpdateAccountTests {

        @Test
        @DisplayName("Should update account successfully")
        void updateAccount_shouldReturnTrue() {
            // Given
            AccountsDto accountsDto = new AccountsDto();
            accountsDto.setAccountNumber(1234567890L);
            accountsDto.setAccountType("Current");
            accountsDto.setBranchAddress("456 Oak Avenue");

            testCustomerDto.setAccountsDto(accountsDto);

            when(accountsRepository.findById(accountsDto.getAccountNumber()))
                    .thenReturn(Optional.of(testAccount));
            when(accountsRepository.save(any(Accounts.class))).thenReturn(testAccount);
            when(customerRepository.findById(testCustomer.getCustomerId()))
                    .thenReturn(Optional.of(testCustomer));
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // When
            boolean result = accountsService.updateAccount(testCustomerDto);

            // Then
            assertThat(result).isTrue();
            verify(accountsRepository).findById(accountsDto.getAccountNumber());
            verify(accountsRepository).save(any(Accounts.class));
            verify(customerRepository).findById(testCustomer.getCustomerId());
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("Should return false when accountsDto is null")
        void updateAccount_shouldReturnFalse_whenAccountsDtoIsNull() {
            // Given
            testCustomerDto.setAccountsDto(null);

            // When
            boolean result = accountsService.updateAccount(testCustomerDto);

            // Then
            assertThat(result).isFalse();
            verify(accountsRepository, never()).findById(anyLong());
            verify(customerRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when account not found")
        void updateAccount_shouldThrowException_whenAccountNotFound() {
            // Given
            AccountsDto accountsDto = new AccountsDto();
            accountsDto.setAccountNumber(9999999999L);
            testCustomerDto.setAccountsDto(accountsDto);

            when(accountsRepository.findById(accountsDto.getAccountNumber()))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> accountsService.updateAccount(testCustomerDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when customer not found during update")
        void updateAccount_shouldThrowException_whenCustomerNotFound() {
            // Given
            AccountsDto accountsDto = new AccountsDto();
            accountsDto.setAccountNumber(1234567890L);
            testCustomerDto.setAccountsDto(accountsDto);

            when(accountsRepository.findById(accountsDto.getAccountNumber()))
                    .thenReturn(Optional.of(testAccount));
            when(accountsRepository.save(any(Accounts.class))).thenReturn(testAccount);
            when(customerRepository.findById(testCustomer.getCustomerId()))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> accountsService.updateAccount(testCustomerDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer not found");
        }
    }

    @Nested
    @DisplayName("deleteAccount() Tests")
    class DeleteAccountTests {

        @Test
        @DisplayName("Should delete account successfully")
        void deleteAccount_shouldReturnTrue() {
            // Given
            when(customerRepository.findByMobileNumber(testCustomer.getMobileNumber()))
                    .thenReturn(Optional.of(testCustomer));

            // When
            boolean result = accountsService.deleteAccount(testCustomer.getMobileNumber());

            // Then
            assertThat(result).isTrue();
            verify(customerRepository).findByMobileNumber(testCustomer.getMobileNumber());
            verify(accountsRepository).deleteByCustomerId(testCustomer.getCustomerId());
            verify(customerRepository).deleteById(testCustomer.getCustomerId());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when customer not found")
        void deleteAccount_shouldThrowException_whenCustomerNotFound() {
            // Given
            String mobileNumber = "0000000000";
            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> accountsService.deleteAccount(mobileNumber))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer not found");

            verify(accountsRepository, never()).deleteByCustomerId(anyLong());
            verify(customerRepository, never()).deleteById(anyLong());
        }
    }
}

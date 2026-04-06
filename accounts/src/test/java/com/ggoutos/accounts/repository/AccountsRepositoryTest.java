package com.ggoutos.accounts.repository;

import com.ggoutos.accounts.entity.Accounts;
import com.ggoutos.accounts.entity.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AccountsRepository Tests")
class AccountsRepositoryTest {

    @Autowired
    private AccountsRepository accountsRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer testCustomer;
    private Accounts testAccount;

    @BeforeEach
    void setUp() {
        // Create and save test customer
        testCustomer = new Customer();
        testCustomer.setName("Test Customer");
        testCustomer.setEmail("test@example.com");
        testCustomer.setMobileNumber("9999999999");
        testCustomer = customerRepository.save(testCustomer);

        // Create and save test account
        testAccount = new Accounts();
        testAccount.setAccountNumber(1234567890L);
        testAccount.setCustomerId(testCustomer.getCustomerId());
        testAccount.setAccountType("Savings");
        testAccount.setBranchAddress("123 Test Street");
        testAccount = accountsRepository.save(testAccount);
    }

    @Nested
    @DisplayName("findByCustomerId() Tests")
    class FindByCustomerIdTests {

        @Test
        @DisplayName("Should return account when customer ID exists")
        void findByCustomerId_shouldReturnAccount() {
            // When
            Optional<Accounts> result = accountsRepository.findByCustomerId(testCustomer.getCustomerId());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getAccountNumber()).isEqualTo(testAccount.getAccountNumber());
            assertThat(result.get().getAccountType()).isEqualTo(testAccount.getAccountType());
            assertThat(result.get().getCustomerId()).isEqualTo(testCustomer.getCustomerId());
        }

        @Test
        @DisplayName("Should return empty when customer ID does not exist")
        void findByCustomerId_shouldReturnEmpty_whenCustomerNotFound() {
            // When
            Optional<Accounts> result = accountsRepository.findByCustomerId(9999L);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByCustomerId() Tests")
    class DeleteByCustomerIdTests {

        @Test
        @DisplayName("Should delete account by customer ID")
        void deleteByCustomerId_shouldDeleteAccount() {
            // When
            accountsRepository.deleteByCustomerId(testCustomer.getCustomerId());

            // Then
            Optional<Accounts> result = accountsRepository.findByCustomerId(testCustomer.getCustomerId());
            assertThat(result).isEmpty();
        }
    }
}

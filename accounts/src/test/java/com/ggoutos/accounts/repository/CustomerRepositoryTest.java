package com.ggoutos.accounts.repository;

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
@DisplayName("CustomerRepository Tests")
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setName("Test Customer");
        testCustomer.setEmail("test@example.com");
        testCustomer.setMobileNumber("9999999999");
        customerRepository.save(testCustomer);
    }

    @Nested
    @DisplayName("findByMobileNumber() Tests")
    class FindByMobileNumberTests {

        @Test
        @DisplayName("Should return customer when mobile number exists")
        void findByMobileNumber_shouldReturnCustomer() {
            // When
            Optional<Customer> result = customerRepository.findByMobileNumber(testCustomer.getMobileNumber());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo(testCustomer.getName());
            assertThat(result.get().getEmail()).isEqualTo(testCustomer.getEmail());
            assertThat(result.get().getMobileNumber()).isEqualTo(testCustomer.getMobileNumber());
        }

        @Test
        @DisplayName("Should return empty when mobile number does not exist")
        void findByMobileNumber_shouldReturnEmpty_whenCustomerNotFound() {
            // When
            Optional<Customer> result = customerRepository.findByMobileNumber("0000000000");

            // Then
            assertThat(result).isEmpty();
        }
    }
}

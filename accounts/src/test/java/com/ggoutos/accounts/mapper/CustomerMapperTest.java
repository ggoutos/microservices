package com.ggoutos.accounts.mapper;

import com.ggoutos.accounts.entity.Customer;
import com.ggoutos.utils.dto.CustomerDetailsDto;
import com.ggoutos.utils.dto.CustomerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomerMapper Tests")
class CustomerMapperTest {

    private Customer testCustomer;
    private CustomerDto testCustomerDto;
    private CustomerDetailsDto testCustomerDetailsDto;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setName("John Doe");
        testCustomer.setEmail("john@example.com");
        testCustomer.setMobileNumber("9939321212");

        testCustomerDto = new CustomerDto();
        testCustomerDetailsDto = new CustomerDetailsDto();
    }

    @Nested
    @DisplayName("mapToCustomerDto() Tests")
    class MapToCustomerDtoTests {

        @Test
        @DisplayName("Should map Customer entity to CustomerDto")
        void mapToCustomerDto_shouldMapCorrectly() {
            // When
            CustomerDto result = CustomerMapper.mapToCustomerDto(testCustomer, testCustomerDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("John Doe");
            assertThat(result.getEmail()).isEqualTo("john@example.com");
            assertThat(result.getMobileNumber()).isEqualTo("9939321212");
        }
    }

    @Nested
    @DisplayName("mapToCustomerDetailsDto() Tests")
    class MapToCustomerDetailsDtoTests {

        @Test
        @DisplayName("Should map Customer entity to CustomerDetailsDto")
        void mapToCustomerDetailsDto_shouldMapCorrectly() {
            // When
            CustomerDetailsDto result = CustomerMapper.mapToCustomerDetailsDto(testCustomer, testCustomerDetailsDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("John Doe");
            assertThat(result.getEmail()).isEqualTo("john@example.com");
            assertThat(result.getMobileNumber()).isEqualTo("9939321212");
        }
    }

    @Nested
    @DisplayName("mapToCustomer() Tests")
    class MapToCustomerTests {

        @Test
        @DisplayName("Should map CustomerDto to Customer entity")
        void mapToCustomer_shouldMapCorrectly() {
            // Given
            CustomerDto customerDto = new CustomerDto();
            customerDto.setName("Jane Doe");
            customerDto.setEmail("jane@example.com");
            customerDto.setMobileNumber("9876543210");

            Customer customer = new Customer();

            // When
            Customer result = CustomerMapper.mapToCustomer(customerDto, customer);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Jane Doe");
            assertThat(result.getEmail()).isEqualTo("jane@example.com");
            assertThat(result.getMobileNumber()).isEqualTo("9876543210");
        }
    }
}

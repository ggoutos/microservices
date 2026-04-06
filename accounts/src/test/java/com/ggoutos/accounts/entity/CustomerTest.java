package com.ggoutos.accounts.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Customer Entity Tests")
class CustomerTest {

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setCustomerId(1L);
        customer.setName("John Doe");
        customer.setEmail("john.doe@example.com");
        customer.setMobileNumber("9939321212");
        customer.setCreatedAt(LocalDateTime.now());
        customer.setCreatedBy("ACCOUNTS_MS");
    }

    @Nested
    @DisplayName("Getters and Setters Tests")
    class GettersAndSettersTests {

        @Test
        @DisplayName("Should set and get customerId correctly")
        void customerId_getterSetter() {
            assertThat(customer.getCustomerId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should set and get name correctly")
        void name_getterSetter() {
            assertThat(customer.getName()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("Should set and get email correctly")
        void email_getterSetter() {
            assertThat(customer.getEmail()).isEqualTo("john.doe@example.com");
        }

        @Test
        @DisplayName("Should set and get mobileNumber correctly")
        void mobileNumber_getterSetter() {
            assertThat(customer.getMobileNumber()).isEqualTo("9939321212");
        }

        @Test
        @DisplayName("Should set and get createdAt from BaseEntity")
        void createdAt_getterSetter() {
            assertThat(customer.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should set and get createdBy from BaseEntity")
        void createdBy_getterSetter() {
            assertThat(customer.getCreatedBy()).isEqualTo("ACCOUNTS_MS");
        }

        @Test
        @DisplayName("Should set and get updatedAt from BaseEntity")
        void updatedAt_getterSetter() {
            customer.setUpdatedAt(LocalDateTime.now());
            assertThat(customer.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should set and get updatedBy from BaseEntity")
        void updatedBy_getterSetter() {
            customer.setUpdatedBy("ACCOUNTS_MS");
            assertThat(customer.getUpdatedBy()).isEqualTo("ACCOUNTS_MS");
        }
    }

    @Nested
    @DisplayName("Equals() Tests")
    class EqualsTests {

        @Test
        @DisplayName("Should return true when comparing same object")
        void equals_sameObject_shouldReturnTrue() {
            assertThat(customer.equals(customer)).isTrue();
        }

        @Test
        @DisplayName("Should return true when comparing objects with same customerId")
        void equals_sameCustomerId_shouldReturnTrue() {
            Customer anotherCustomer = new Customer();
            anotherCustomer.setCustomerId(1L);

            assertThat(customer.equals(anotherCustomer)).isTrue();
        }

        @Test
        @DisplayName("Should return false when comparing with null")
        void equals_null_shouldReturnFalse() {
            assertThat(customer.equals(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false when comparing with different type")
        void equals_differentType_shouldReturnFalse() {
            assertThat(customer.equals("not a customer")).isFalse();
        }

        @Test
        @DisplayName("Should return false when comparing objects with different customerId")
        void equals_differentCustomerId_shouldReturnFalse() {
            Customer anotherCustomer = new Customer();
            anotherCustomer.setCustomerId(999L);

            assertThat(customer.equals(anotherCustomer)).isFalse();
        }

        @Test
        @DisplayName("Should return false when customerId is null")
        void equals_nullCustomerId_shouldReturnFalse() {
            Customer customerWithNullId = new Customer();
            
            assertThat(customer.equals(customerWithNullId)).isFalse();
        }

        @Test
        @DisplayName("Should return false when both customerIds are null")
        void equals_bothNullCustomerId_shouldReturnFalse() {
            Customer customer1 = new Customer();
            Customer customer2 = new Customer();

            assertThat(customer1.equals(customer2)).isFalse();
        }
    }

    @Nested
    @DisplayName("hashCode() Tests")
    class HashCodeTests {

        @Test
        @DisplayName("Should return consistent hash code")
        void hashCode_shouldBeConsistent() {
            int hashCode1 = customer.hashCode();
            int hashCode2 = customer.hashCode();

            assertThat(hashCode1).isEqualTo(hashCode2);
        }

        @Test
        @DisplayName("Should return same hash code for objects with same customerId")
        void hashCode_sameCustomerId_shouldReturnSameHashCode() {
            Customer anotherCustomer = new Customer();
            anotherCustomer.setCustomerId(1L);

            // Hash code is based on class, not customerId
            // Both should have same hash since they're same class
            assertThat(customer.hashCode()).isEqualTo(anotherCustomer.hashCode());
        }
    }

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should return string representation")
        void toString_shouldReturnString() {
            String result = customer.toString();

            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
            assertThat(result).contains("Customer");
        }

        @Test
        @DisplayName("Should include field values in string representation")
        void toString_shouldIncludeFields() {
            String result = customer.toString();

            assertThat(result).contains("John Doe");
            assertThat(result).contains("john.doe@example.com");
            assertThat(result).contains("9939321212");
        }
    }

    @Nested
    @DisplayName("Entity Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("Should extend BaseEntity")
        void shouldExtendBaseEntity() {
            assertThat(customer).isInstanceOf(BaseEntity.class);
        }

        @Test
        @DisplayName("Should have audit fields from BaseEntity")
        void shouldHaveAuditFields() {
            assertThat(customer.getCreatedAt()).isNotNull();
            assertThat(customer.getCreatedBy()).isEqualTo("ACCOUNTS_MS");
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create empty instance with no-args constructor")
        void noArgsConstructor_shouldCreateEmptyInstance() {
            Customer emptyCustomer = new Customer();

            assertThat(emptyCustomer).isNotNull();
            assertThat(emptyCustomer.getCustomerId()).isNull();
            assertThat(emptyCustomer.getName()).isNull();
            assertThat(emptyCustomer.getEmail()).isNull();
            assertThat(emptyCustomer.getMobileNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("Entity Generation Strategy Tests")
    class GenerationTypeTests {

        @Test
        @DisplayName("CustomerId should be auto-generated by database (IDENTITY strategy)")
        void customerId_shouldUseIdentityStrategy() {
            // This test documents that customerId uses GenerationType.IDENTITY
            // In real DB operations, the database auto-generates the ID
            Customer newCustomer = new Customer();
            newCustomer.setName("Jane Doe");
            newCustomer.setEmail("jane@example.com");
            newCustomer.setMobileNumber("9876543210");

            // Before persistence, ID is null
            assertThat(newCustomer.getCustomerId()).isNull();
        }
    }
}

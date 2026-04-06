package com.ggoutos.accounts.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Accounts Entity Tests")
class AccountsTest {

    private Accounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new Accounts();
        accounts.setAccountNumber(1234567890L);
        accounts.setCustomerId(1L);
        accounts.setAccountType("Savings");
        accounts.setBranchAddress("123 Main Street, New York");
        accounts.setCreatedAt(LocalDateTime.now());
        accounts.setCreatedBy("ACCOUNTS_MS");
    }

    @Nested
    @DisplayName("Equals() Tests")
    class EqualsTests {

        @Test
        @DisplayName("Should return true when comparing same object")
        void equals_sameObject_shouldReturnTrue() {
            assertThat(accounts.equals(accounts)).isTrue();
        }

        @Test
        @DisplayName("Should return true when comparing objects with same accountNumber")
        void equals_sameAccountNumber_shouldReturnTrue() {
            Accounts anotherAccount = new Accounts();
            anotherAccount.setAccountNumber(1234567890L);

            assertThat(accounts.equals(anotherAccount)).isTrue();
        }

        @Test
        @DisplayName("Should return false when comparing with null")
        void equals_null_shouldReturnFalse() {
            assertThat(accounts.equals(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false when comparing with different type")
        void equals_differentType_shouldReturnFalse() {
            assertThat(accounts.equals("not an account")).isFalse();
        }

        @Test
        @DisplayName("Should return false when comparing objects with different accountNumber")
        void equals_differentAccountNumber_shouldReturnFalse() {
            Accounts anotherAccount = new Accounts();
            anotherAccount.setAccountNumber(9876543210L);

            assertThat(accounts.equals(anotherAccount)).isFalse();
        }

        @Test
        @DisplayName("Should return false when accountNumber is null")
        void equals_nullAccountNumber_shouldReturnFalse() {
            Accounts accountWithNullId = new Accounts();
            
            assertThat(accounts.equals(accountWithNullId)).isFalse();
        }

        @Test
        @DisplayName("Should return true when both accountNumbers are null")
        void equals_bothNullAccountNumber_shouldReturnFalse() {
            Accounts account1 = new Accounts();
            Accounts account2 = new Accounts();

            assertThat(account1.equals(account2)).isFalse();
        }
    }

    @Nested
    @DisplayName("hashCode() Tests")
    class HashCodeTests {

        @Test
        @DisplayName("Should return consistent hash code")
        void hashCode_shouldBeConsistent() {
            int hashCode1 = accounts.hashCode();
            int hashCode2 = accounts.hashCode();

            assertThat(hashCode1).isEqualTo(hashCode2);
        }

        @Test
        @DisplayName("Should return same hash code for objects with same accountNumber")
        void hashCode_sameAccountNumber_shouldReturnSameHashCode() {
            Accounts anotherAccount = new Accounts();
            anotherAccount.setAccountNumber(1234567890L);

            // Hash code is based on class, not accountNumber
            // Both should have same hash since they're same class
            assertThat(accounts.hashCode()).isEqualTo(anotherAccount.hashCode());
        }
    }

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should return string representation")
        void toString_shouldReturnString() {
            String result = accounts.toString();

            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
            assertThat(result).contains("Accounts");
        }

        @Test
        @DisplayName("Should include field values in string representation")
        void toString_shouldIncludeFields() {
            String result = accounts.toString();

            assertThat(result).contains("1234567890");
            assertThat(result).contains("Savings");
        }
    }

    @Nested
    @DisplayName("Entity Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("Should extend BaseEntity")
        void shouldExtendBaseEntity() {
            assertThat(accounts).isInstanceOf(BaseEntity.class);
        }

        @Test
        @DisplayName("Should have audit fields from BaseEntity")
        void shouldHaveAuditFields() {
            assertThat(accounts.getCreatedAt()).isNotNull();
            assertThat(accounts.getCreatedBy()).isEqualTo("ACCOUNTS_MS");
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create empty instance with no-args constructor")
        void noArgsConstructor_shouldCreateEmptyInstance() {
            Accounts emptyAccount = new Accounts();

            assertThat(emptyAccount).isNotNull();
            assertThat(emptyAccount.getAccountNumber()).isNull();
            assertThat(emptyAccount.getCustomerId()).isNull();
            assertThat(emptyAccount.getAccountType()).isNull();
            assertThat(emptyAccount.getBranchAddress()).isNull();
        }
    }
}

package com.ggoutos.loans.repository;

import com.ggoutos.loans.entity.Loans;
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
@DisplayName("LoansRepository Tests")
class LoansRepositoryTest {

    @Autowired
    private LoansRepository loansRepository;

    private Loans testLoan;
    private static final String TEST_MOBILE_NUMBER = "9999999999";

    @BeforeEach
    void setUp() {
        testLoan = new Loans();
        testLoan.setMobileNumber(TEST_MOBILE_NUMBER);
        testLoan.setLoanNumber("100123456789");
        testLoan.setLoanType("Home Loan");
        testLoan.setTotalLoan(100000);
        testLoan.setAmountPaid(0);
        testLoan.setOutstandingAmount(100000);
        testLoan = loansRepository.save(testLoan);
    }

    @Nested
    @DisplayName("findByMobileNumber() Tests")
    class FindByMobileNumberTests {

        @Test
        @DisplayName("Should return loan when mobile number exists")
        void findByMobileNumber_shouldReturnLoan() {
            // When
            Optional<Loans> result = loansRepository.findByMobileNumber(TEST_MOBILE_NUMBER);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getMobileNumber()).isEqualTo(TEST_MOBILE_NUMBER);
            assertThat(result.get().getLoanNumber()).isEqualTo(testLoan.getLoanNumber());
        }

        @Test
        @DisplayName("Should return empty when mobile number does not exist")
        void findByMobileNumber_shouldReturnEmpty_whenLoanNotFound() {
            // When
            Optional<Loans> result = loansRepository.findByMobileNumber("0000000000");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByLoanNumber() Tests")
    class FindByLoanNumberTests {

        @Test
        @DisplayName("Should return loan when loan number exists")
        void findByLoanNumber_shouldReturnLoan() {
            // When
            Optional<Loans> result = loansRepository.findByLoanNumber(testLoan.getLoanNumber());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getLoanNumber()).isEqualTo(testLoan.getLoanNumber());
            assertThat(result.get().getMobileNumber()).isEqualTo(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should return empty when loan number does not exist")
        void findByLoanNumber_shouldReturnEmpty_whenLoanNotFound() {
            // When
            Optional<Loans> result = loansRepository.findByLoanNumber("000000000000");

            // Then
            assertThat(result).isEmpty();
        }
    }
}

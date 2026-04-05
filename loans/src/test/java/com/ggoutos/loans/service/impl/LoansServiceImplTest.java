package com.ggoutos.loans.service.impl;

import com.ggoutos.loans.constants.LoansConstants;
import com.ggoutos.loans.entity.Loans;
import com.ggoutos.loans.exception.LoanAlreadyExistsException;
import com.ggoutos.loans.exception.ResourceNotFoundException;
import com.ggoutos.loans.repository.LoansRepository;
import com.ggoutos.utils.dto.LoansDto;
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
@DisplayName("LoansServiceImpl Tests")
class LoansServiceImplTest {

    @Mock
    private LoansRepository loansRepository;

    @InjectMocks
    private LoansServiceImpl loansService;

    private Loans testLoan;
    private LoansDto testLoansDto;
    private static final String TEST_MOBILE_NUMBER = "9939321212";

    @BeforeEach
    void setUp() {
        testLoan = new Loans();
        testLoan.setLoanId(1L);
        testLoan.setMobileNumber(TEST_MOBILE_NUMBER);
        testLoan.setLoanNumber("100123456789");
        testLoan.setLoanType(LoansConstants.HOME_LOAN);
        testLoan.setTotalLoan(LoansConstants.NEW_LOAN_LIMIT);
        testLoan.setAmountPaid(0);
        testLoan.setOutstandingAmount(LoansConstants.NEW_LOAN_LIMIT);

        testLoansDto = new LoansDto();
        testLoansDto.setMobileNumber(TEST_MOBILE_NUMBER);
        testLoansDto.setLoanNumber("100123456789");
        testLoansDto.setLoanType("Home Loan");
        testLoansDto.setTotalLoan(100000);
        testLoansDto.setAmountPaid(10000);
        testLoansDto.setOutstandingAmount(90000);
    }

    @Nested
    @DisplayName("createLoan() Tests")
    class CreateLoanTests {

        @Test
        @DisplayName("Should create loan successfully")
        void createLoan_shouldSaveLoan() {
            // Given
            when(loansRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.empty());
            when(loansRepository.save(any(Loans.class))).thenReturn(testLoan);

            // When
            loansService.createLoan(TEST_MOBILE_NUMBER);

            // Then
            verify(loansRepository).findByMobileNumber(TEST_MOBILE_NUMBER);
            verify(loansRepository).save(argThat(loan ->
                    loan.getMobileNumber().equals(TEST_MOBILE_NUMBER) &&
                    loan.getLoanType().equals(LoansConstants.HOME_LOAN) &&
                    loan.getTotalLoan()==LoansConstants.NEW_LOAN_LIMIT &&
                    loan.getAmountPaid()==0 &&
                    loan.getOutstandingAmount()==LoansConstants.NEW_LOAN_LIMIT &&
                    loan.getLoanNumber().matches("\\d{12}")
            ));
        }

        @Test
        @DisplayName("Should throw LoanAlreadyExistsException when loan already exists")
        void createLoan_shouldThrowException_whenLoanAlreadyExists() {
            // Given
            when(loansRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.of(testLoan));

            // When & Then
            assertThatThrownBy(() -> loansService.createLoan(TEST_MOBILE_NUMBER))
                    .isInstanceOf(LoanAlreadyExistsException.class)
                    .hasMessageContaining("Loan already registered with given mobileNumber");

            verify(loansRepository, never()).save(any(Loans.class));
        }
    }

    @Nested
    @DisplayName("fetchLoan() Tests")
    class FetchLoanTests {

        @Test
        @DisplayName("Should fetch loan successfully")
        void fetchLoan_shouldReturnLoansDto() {
            // Given
            when(loansRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.of(testLoan));

            // When
            LoansDto result = loansService.fetchLoan(TEST_MOBILE_NUMBER);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMobileNumber()).isEqualTo(TEST_MOBILE_NUMBER);
            assertThat(result.getLoanNumber()).isEqualTo(testLoan.getLoanNumber());
            assertThat(result.getLoanType()).isEqualTo(testLoan.getLoanType());
            assertThat(result.getTotalLoan()).isEqualTo(testLoan.getTotalLoan());
            assertThat(result.getAmountPaid()).isEqualTo(testLoan.getAmountPaid());
            assertThat(result.getOutstandingAmount()).isEqualTo(testLoan.getOutstandingAmount());

            verify(loansRepository).findByMobileNumber(TEST_MOBILE_NUMBER);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when loan not found")
        void fetchLoan_shouldThrowException_whenLoanNotFound() {
            // Given
            when(loansRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> loansService.fetchLoan(TEST_MOBILE_NUMBER))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Loan not found");
        }
    }

    @Nested
    @DisplayName("updateLoan() Tests")
    class UpdateLoanTests {

        @Test
        @DisplayName("Should update loan successfully")
        void updateLoan_shouldReturnTrue() {
            // Given
            when(loansRepository.findByLoanNumber(testLoansDto.getLoanNumber())).thenReturn(Optional.of(testLoan));
            when(loansRepository.save(any(Loans.class))).thenReturn(testLoan);

            // When
            boolean result = loansService.updateLoan(testLoansDto);

            // Then
            assertThat(result).isTrue();
            verify(loansRepository).findByLoanNumber(testLoansDto.getLoanNumber());
            verify(loansRepository).save(any(Loans.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when loan not found")
        void updateLoan_shouldThrowException_whenLoanNotFound() {
            // Given
            when(loansRepository.findByLoanNumber(testLoansDto.getLoanNumber())).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> loansService.updateLoan(testLoansDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Loan not found");
        }
    }

    @Nested
    @DisplayName("deleteLoan() Tests")
    class DeleteLoanTests {

        @Test
        @DisplayName("Should delete loan successfully")
        void deleteLoan_shouldReturnTrue() {
            // Given
            when(loansRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.of(testLoan));

            // When
            boolean result = loansService.deleteLoan(TEST_MOBILE_NUMBER);

            // Then
            assertThat(result).isTrue();
            verify(loansRepository).findByMobileNumber(TEST_MOBILE_NUMBER);
            verify(loansRepository).deleteById(testLoan.getLoanId());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when loan not found")
        void deleteLoan_shouldThrowException_whenLoanNotFound() {
            // Given
            when(loansRepository.findByMobileNumber(TEST_MOBILE_NUMBER)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> loansService.deleteLoan(TEST_MOBILE_NUMBER))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Loan not found");

            verify(loansRepository, never()).deleteById(anyLong());
        }
    }
}

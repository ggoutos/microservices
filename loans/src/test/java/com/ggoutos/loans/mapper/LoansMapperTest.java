package com.ggoutos.loans.mapper;

import com.ggoutos.loans.entity.Loans;
import com.ggoutos.utils.dto.LoansDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoansMapper Tests")
class LoansMapperTest {

    @Nested
    @DisplayName("mapToLoansDto() Tests")
    class MapToLoansDtoTests {

        @Test
        @DisplayName("Should map Loans entity to LoansDto")
        void mapToLoansDto_shouldMapCorrectly() {
            // Given
            Loans loans = new Loans();
            loans.setMobileNumber("9939321212");
            loans.setLoanNumber("100123456789");
            loans.setLoanType("Home Loan");
            loans.setTotalLoan(100000);
            loans.setAmountPaid(10000);
            loans.setOutstandingAmount(90000);

            LoansDto loansDto = new LoansDto();

            // When
            LoansDto result = LoansMapper.mapToLoansDto(loans, loansDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMobileNumber()).isEqualTo("9939321212");
            assertThat(result.getLoanNumber()).isEqualTo("100123456789");
            assertThat(result.getLoanType()).isEqualTo("Home Loan");
            assertThat(result.getTotalLoan()).isEqualTo(100000);
            assertThat(result.getAmountPaid()).isEqualTo(10000);
            assertThat(result.getOutstandingAmount()).isEqualTo(90000);
        }
    }

    @Nested
    @DisplayName("mapToLoans() Tests")
    class MapToLoansTests {

        @Test
        @DisplayName("Should map LoansDto to Loans entity")
        void mapToLoans_shouldMapCorrectly() {
            // Given
            LoansDto loansDto = new LoansDto();
            loansDto.setMobileNumber("9939321212");
            loansDto.setLoanNumber("100123456789");
            loansDto.setLoanType("Personal Loan");
            loansDto.setTotalLoan(50000);
            loansDto.setAmountPaid(5000);
            loansDto.setOutstandingAmount(45000);

            Loans loans = new Loans();

            // When
            Loans result = LoansMapper.mapToLoans(loansDto, loans);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMobileNumber()).isEqualTo("9939321212");
            assertThat(result.getLoanNumber()).isEqualTo("100123456789");
            assertThat(result.getLoanType()).isEqualTo("Personal Loan");
            assertThat(result.getTotalLoan()).isEqualTo(50000);
            assertThat(result.getAmountPaid()).isEqualTo(5000);
            assertThat(result.getOutstandingAmount()).isEqualTo(45000);
        }
    }
}

package com.ggoutos.accounts.mapper;

import com.ggoutos.accounts.entity.Accounts;
import com.ggoutos.utils.dto.AccountsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountsMapper Tests")
class AccountsMapperTest {

    @Nested
    @DisplayName("mapToAccountsDto() Tests")
    class MapToAccountsDtoTests {

        @Test
        @DisplayName("Should map Accounts entity to AccountsDto")
        void mapToAccountsDto_shouldMapCorrectly() {
            // Given
            Accounts accounts = new Accounts();
            accounts.setAccountNumber(1234567890L);
            accounts.setAccountType("Savings");
            accounts.setBranchAddress("123 Main Street");

            AccountsDto accountsDto = new AccountsDto();

            // When
            AccountsDto result = AccountsMapper.mapToAccountsDto(accounts, accountsDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAccountNumber()).isEqualTo(1234567890L);
            assertThat(result.getAccountType()).isEqualTo("Savings");
            assertThat(result.getBranchAddress()).isEqualTo("123 Main Street");
        }
    }

    @Nested
    @DisplayName("mapToAccounts() Tests")
    class MapToAccountsTests {

        @Test
        @DisplayName("Should map AccountsDto to Accounts entity")
        void mapToAccounts_shouldMapCorrectly() {
            // Given
            AccountsDto accountsDto = new AccountsDto();
            accountsDto.setAccountNumber(1234567890L);
            accountsDto.setAccountType("Current");
            accountsDto.setBranchAddress("456 Oak Avenue");

            Accounts accounts = new Accounts();

            // When
            Accounts result = AccountsMapper.mapToAccounts(accountsDto, accounts);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAccountNumber()).isEqualTo(1234567890L);
            assertThat(result.getAccountType()).isEqualTo("Current");
            assertThat(result.getBranchAddress()).isEqualTo("456 Oak Avenue");
        }
    }
}

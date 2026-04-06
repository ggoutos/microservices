package com.ggoutos.accounts.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditAwareImpl Tests")
class AuditAwareImplTest {

    private final AuditAwareImpl auditAware = new AuditAwareImpl();

    @Test
    @DisplayName("Should return ACCOUNTS_MS as current auditor")
    void getCurrentAuditor_shouldReturnAccountsMS() {
        // When
        Optional<String> result = auditAware.getCurrentAuditor();

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("ACCOUNTS_MS");
    }
}

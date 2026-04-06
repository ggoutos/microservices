package com.ggoutos.cards.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditAwareImpl Tests")
class AuditAwareImplTest {

    private final AuditAwareImpl auditAware = new AuditAwareImpl();

    @Test
    @DisplayName("Should return CARDS_MS as current auditor")
    void getCurrentAuditor_shouldReturnCardsMS() {
        // When
        Optional<String> result = auditAware.getCurrentAuditor();

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("CARDS_MS");
    }
}

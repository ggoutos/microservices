package com.ggoutos.gatewayserver.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FallbackController Tests")
class FallbackControllerTest {

    private final FallbackController fallbackController = new FallbackController();

    @Test
    @DisplayName("Should return support message")
    void contactSupport_shouldReturnSupportMessage() {
        assertThat(fallbackController.contactSupport().block())
                .contains("contact support");
    }

}

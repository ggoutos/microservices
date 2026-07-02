package com.ggoutos.message.functions;

import com.ggoutos.utils.dto.AccountsMsgDto;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFunctionsTest {

    private final MessageFunctions messageFunctions = new MessageFunctions();

    @Test
    void emailReturnsOriginalMessage() {
        AccountsMsgDto message = new AccountsMsgDto(1234567890L, "Jane Doe", "jane@example.com", "5551234567");

        Function<AccountsMsgDto, AccountsMsgDto> email = messageFunctions.email();

        assertThat(email.apply(message)).isSameAs(message);
    }

    @Test
    void smsReturnsAccountNumber() {
        AccountsMsgDto message = new AccountsMsgDto(1234567890L, "Jane Doe", "jane@example.com", "5551234567");

        Function<AccountsMsgDto, Long> sms = messageFunctions.sms();

        assertThat(sms.apply(message)).isEqualTo(1234567890L);
    }

}

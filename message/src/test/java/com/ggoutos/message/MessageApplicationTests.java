package com.ggoutos.message;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(properties = "spring.cloud.stream.default-binder=integration")
@Import(TestChannelBinderConfiguration.class)
class MessageApplicationTests {

    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private OutputDestination outputDestination;

    @Test
    void contextLoads() {
        assertThat(inputDestination).isNotNull();
        assertThat(outputDestination).isNotNull();
    }

    @Test
    void testMainMethodIsDeclared() {
        assertDoesNotThrow(() -> MessageApplication.class.getDeclaredMethod("main", String[].class));
    }

}

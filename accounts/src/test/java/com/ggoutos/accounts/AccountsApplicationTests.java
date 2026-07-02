package com.ggoutos.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binding.InputBindingLifecycle;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class AccountsApplicationTests {

    @MockitoBean
    private InputBindingLifecycle inputBindingLifecycle;

    @Test
    void contextLoads() {
    }

    @Test
    void testMainMethodIsDeclared() {
        assertDoesNotThrow(() -> AccountsApplication.class.getDeclaredMethod("main", String[].class));
    }

}

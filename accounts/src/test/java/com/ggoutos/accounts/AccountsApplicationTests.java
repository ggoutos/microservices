package com.ggoutos.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class AccountsApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void testMainMethod() {
        assertDoesNotThrow(() -> AccountsApplication.main(new String[]{}));
    }

}

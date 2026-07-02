package com.ggoutos.gatewayserver;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.mockito.Mockito.mockStatic;

@SpringBootTest
class GatewayserverApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void testMainMethod() {
        String[] args = new String[] {};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            GatewayserverApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(GatewayserverApplication.class, args));
        }
    }

}

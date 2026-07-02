package com.ggoutos.accounts.config;

import com.ggoutos.accounts.service.IAccountsService;
import com.ggoutos.utils.dto.CustomerDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(com.ggoutos.accounts.controller.AccountsController.class)
@Import(SecurityConfig.class)
@DisplayName("Accounts SecurityConfig Tests")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAccountsService accountsService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Should reject unauthenticated business requests")
    void businessRequest_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/fetch").param("mobileNumber", "9939321212"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject requests without ACCOUNTS role")
    void businessRequest_shouldReturn403_whenRoleMissing() throws Exception {
        mockMvc.perform(get("/api/fetch")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CARDS")))
                        .param("mobileNumber", "9939321212"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow requests with ACCOUNTS role")
    void businessRequest_shouldReturn200_whenRolePresent() throws Exception {
        CustomerDto customerDto = new CustomerDto();
        customerDto.setName("John Doe");
        customerDto.setEmail("john@example.com");
        customerDto.setMobileNumber("9939321212");
        when(accountsService.fetchAccount("9939321212")).thenReturn(customerDto);

        mockMvc.perform(get("/api/fetch")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ACCOUNTS")))
                        .param("mobileNumber", "9939321212"))
                .andExpect(status().isOk());
    }
}

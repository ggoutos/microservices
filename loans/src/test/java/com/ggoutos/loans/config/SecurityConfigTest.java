package com.ggoutos.loans.config;

import com.ggoutos.loans.service.ILoansService;
import com.ggoutos.utils.dto.LoansDto;
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

@WebMvcTest(com.ggoutos.loans.controller.LoansController.class)
@Import(SecurityConfig.class)
@DisplayName("Loans SecurityConfig Tests")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ILoansService loansService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Should reject unauthenticated business requests")
    void businessRequest_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/fetch")
                        .header("eazybank-correlation-id", "test-correlation-id")
                        .param("mobileNumber", "9939321212"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject requests without LOANS role")
    void businessRequest_shouldReturn403_whenRoleMissing() throws Exception {
        mockMvc.perform(get("/api/fetch")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ACCOUNTS")))
                        .header("eazybank-correlation-id", "test-correlation-id")
                        .param("mobileNumber", "9939321212"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow requests with LOANS role")
    void businessRequest_shouldReturn200_whenRolePresent() throws Exception {
        LoansDto loansDto = new LoansDto();
        loansDto.setMobileNumber("9939321212");
        loansDto.setLoanNumber("100123456789");
        loansDto.setLoanType("Home Loan");
        when(loansService.fetchLoan("9939321212")).thenReturn(loansDto);

        mockMvc.perform(get("/api/fetch")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LOANS")))
                        .header("eazybank-correlation-id", "test-correlation-id")
                        .param("mobileNumber", "9939321212"))
                .andExpect(status().isOk());
    }
}

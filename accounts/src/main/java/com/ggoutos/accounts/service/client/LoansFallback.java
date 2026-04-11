package com.ggoutos.accounts.service.client;

import com.ggoutos.utils.dto.LoansDto;
import org.springframework.stereotype.Component;

@Component
public class LoansFallback implements LoansFeignClient {

    @Override
    public LoansDto fetchLoanDetails(String correlationId, String mobileNumber) {
        return null;
    }
}

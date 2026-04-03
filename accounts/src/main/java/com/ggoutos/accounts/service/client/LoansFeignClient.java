package com.ggoutos.accounts.service.client;

import com.ggoutos.utils.dto.LoansDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "loans")
public interface LoansFeignClient {

    @GetMapping(value = "/api/fetch", consumes = "application/json")
    LoansDto fetchLoanDetails(@RequestHeader(name = "eazybank-correlation-id", required = true) String correlationId, @RequestParam String mobileNumber);

}

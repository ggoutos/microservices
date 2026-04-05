package com.ggoutos.accounts.service.impl;

import com.ggoutos.accounts.entity.Accounts;
import com.ggoutos.accounts.entity.Customer;
import com.ggoutos.accounts.mapper.AccountsMapper;
import com.ggoutos.accounts.mapper.CustomerMapper;
import com.ggoutos.accounts.repository.AccountsRepository;
import com.ggoutos.accounts.repository.CustomerRepository;
import com.ggoutos.accounts.service.ICustomersService;
import com.ggoutos.accounts.service.client.CardsFeignClient;
import com.ggoutos.accounts.service.client.LoansFeignClient;
import com.ggoutos.utils.dto.AccountsDto;
import com.ggoutos.utils.dto.CustomerDetailsDto;
import com.ggoutos.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomersServiceImpl implements ICustomersService {

    private final AccountsRepository accountsRepository;
    private final CustomerRepository customerRepository;
    private final CardsFeignClient cardsFeignClient;
    private final LoansFeignClient loansFeignClient;

    /**
     * @param mobileNumber - Input Mobile Number
     * @return Customer Details based on a given mobileNumber
     */
    @Override
    public CustomerDetailsDto fetchCustomerDetails(String mobileNumber, String correlationId) {
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
        );
        Accounts accounts = accountsRepository.findByCustomerId(customer.getCustomerId()).orElseThrow(
                () -> new ResourceNotFoundException("Account", "customerId", customer.getCustomerId().toString())
        );

        CustomerDetailsDto customerDetailsDto = CustomerMapper.mapToCustomerDetailsDto(customer, new CustomerDetailsDto());
        customerDetailsDto.setAccountsDto(AccountsMapper.mapToAccountsDto(accounts, new AccountsDto()));

        customerDetailsDto.setLoansDto(loansFeignClient.fetchLoanDetails(correlationId, mobileNumber));
        customerDetailsDto.setCardsDto(cardsFeignClient.fetchCardDetails(correlationId, mobileNumber));

        return customerDetailsDto;

    }
}

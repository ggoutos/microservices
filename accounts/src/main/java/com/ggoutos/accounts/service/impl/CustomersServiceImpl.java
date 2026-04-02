package com.ggoutos.accounts.service.impl;

import com.ggoutos.accounts.entity.Accounts;
import com.ggoutos.accounts.entity.Customer;
import com.ggoutos.accounts.exception.ResourceNotFoundException;
import com.ggoutos.accounts.mapper.AccountsMapper;
import com.ggoutos.accounts.mapper.CustomerMapper;
import com.ggoutos.accounts.repository.AccountsRepository;
import com.ggoutos.accounts.repository.CustomerRepository;
import com.ggoutos.accounts.service.ICustomersService;
import com.ggoutos.accounts.service.client.CardsFeignClient;
import com.ggoutos.accounts.service.client.LoansFeignClient;
import com.ggoutos.utils.dto.AccountsDto;
import com.ggoutos.utils.dto.CardsDto;
import com.ggoutos.utils.dto.CustomerDetailsDto;
import com.ggoutos.utils.dto.LoansDto;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CustomersServiceImpl implements ICustomersService {

    private AccountsRepository accountsRepository;
    private CustomerRepository customerRepository;
    private CardsFeignClient cardsFeignClient;
    private LoansFeignClient loansFeignClient;

    /**
     * @param mobileNumber - Input Mobile Number
     * @return Customer Details based on a given mobileNumber
     */
    @Override
    public CustomerDetailsDto fetchCustomerDetails(String mobileNumber) {
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
        );
        Accounts accounts = accountsRepository.findByCustomerId(customer.getCustomerId()).orElseThrow(
                () -> new ResourceNotFoundException("Account", "customerId", customer.getCustomerId().toString())
        );

        CustomerDetailsDto customerDetailsDto = CustomerMapper.mapToCustomerDetailsDto(customer, new CustomerDetailsDto());
        customerDetailsDto.setAccountsDto(AccountsMapper.mapToAccountsDto(accounts, new AccountsDto()));

        ResponseEntity<LoansDto> loansDtoResponseEntity = loansFeignClient.fetchLoanDetails(mobileNumber);
        customerDetailsDto.setLoansDto(loansDtoResponseEntity.getBody());

        ResponseEntity<CardsDto> cardsDtoResponseEntity = cardsFeignClient.fetchCardDetails(mobileNumber);
        customerDetailsDto.setCardsDto(cardsDtoResponseEntity.getBody());

        return customerDetailsDto;

    }
}

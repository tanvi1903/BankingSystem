package com.banking.bankingsystem.service;

import com.banking.bankingsystem.dto.response.AccountResponse;
import com.banking.bankingsystem.entity.Account;
import com.banking.bankingsystem.exception.AccountNotFoundException;
import com.banking.bankingsystem.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByCustomerId(String customerId) {
        return accountRepository.findByCustomerCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .customerId(account.getCustomer() != null ? account.getCustomer().getCustomerId() : null)
                .customerName(account.getCustomer() != null ? account.getCustomer().getFullName() : null)
                .customerEmail(account.getCustomer() != null ? account.getCustomer().getEmail() : null)
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
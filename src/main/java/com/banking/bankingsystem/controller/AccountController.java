package com.banking.bankingsystem.controller;

import com.banking.bankingsystem.dto.response.AccountResponse;
import com.banking.bankingsystem.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable String accountId) {
        return ResponseEntity.ok(accountService.getAccountById(accountId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByCustomer(@PathVariable String customerId) {
        return ResponseEntity.ok(accountService.getAccountsByCustomerId(customerId));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<Object> getAccountBalance(@PathVariable String accountId) {
        AccountResponse account = accountService.getAccountById(accountId);
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("accountId", account.getAccountId());
        response.put("customerId", account.getCustomerId());
        response.put("customerName", account.getCustomerName());
        response.put("accountType", account.getAccountType());
        response.put("balance", account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO);
        response.put("status", account.getStatus());
        return ResponseEntity.ok(response);
    }
}
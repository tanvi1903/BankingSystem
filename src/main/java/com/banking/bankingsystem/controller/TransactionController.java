package com.banking.bankingsystem.controller;

import com.banking.bankingsystem.dto.request.TransactionRequest;
import com.banking.bankingsystem.dto.response.TransactionResponse;
import com.banking.bankingsystem.entity.Transaction;
import com.banking.bankingsystem.enums.TransactionType;
import com.banking.bankingsystem.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/process")
    public ResponseEntity<TransactionResponse> processTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.processTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody TransactionRequest request) {
        request.setType(TransactionType.DEPOSIT);
        TransactionResponse response = transactionService.processTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody TransactionRequest request) {
        request.setType(TransactionType.WITHDRAWAL);
        TransactionResponse response = transactionService.processTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransactionRequest request) {
        request.setType(TransactionType.TRANSFER);
        TransactionResponse response = transactionService.processTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Transaction>> getAccountHistory(@PathVariable String accountId) {
        return ResponseEntity.ok(transactionService.getAccountHistory(accountId));
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }
}
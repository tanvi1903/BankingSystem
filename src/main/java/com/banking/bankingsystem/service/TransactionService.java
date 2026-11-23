package com.banking.bankingsystem.service;

import com.banking.bankingsystem.dto.request.TransactionRequest;
import com.banking.bankingsystem.dto.response.TransactionResponse;
import com.banking.bankingsystem.entity.Transaction;

import java.util.List;

public interface TransactionService {
    TransactionResponse processTransaction(TransactionRequest request);
    List<Transaction> getAccountHistory(String accountId);
    List<Transaction> getAllTransactions();
}
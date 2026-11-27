package com.banking.bankingsystem.controller;

import com.banking.bankingsystem.entity.Account;
import com.banking.bankingsystem.entity.FailedTransaction;
import com.banking.bankingsystem.entity.Transaction;
import com.banking.bankingsystem.entity.TransactionAudit;
import com.banking.bankingsystem.enums.TransactionStatus;
import com.banking.bankingsystem.repository.AccountRepository;
import com.banking.bankingsystem.repository.FailedTransactionRepository;
import com.banking.bankingsystem.repository.TransactionAuditRepository;
import com.banking.bankingsystem.repository.TransactionRepository;
import com.banking.bankingsystem.service.ConcurrencySimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionAuditRepository transactionAuditRepository;
    private final FailedTransactionRepository failedTransactionRepository;
    private final ConcurrencySimulationService simulationService;

    public AdminController(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           TransactionAuditRepository transactionAuditRepository,
                           FailedTransactionRepository failedTransactionRepository,
                           ConcurrencySimulationService simulationService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionAuditRepository = transactionAuditRepository;
        this.failedTransactionRepository = failedTransactionRepository;
        this.simulationService = simulationService;
    }

    @PostMapping("/benchmark")
    public ResponseEntity runMixed50kBenchmark(
            @RequestParam(defaultValue = "10") int threads,
            @RequestParam(defaultValue = "50000") int totalTransactions) throws InterruptedException {
        return ResponseEntity.ok(simulationService.simulateMixed50kBenchmark(threads, totalTransactions));
    }

    @GetMapping("/failed-transactions")
    public ResponseEntity getFailedTransactions() {
        return ResponseEntity.ok(failedTransactionRepository.findAll());
    }

    @GetMapping("/audits")
    public ResponseEntity getAuditTrail() {
        return ResponseEntity.ok(transactionAuditRepository.findAll());
    }

    @GetMapping("/summary")
    public ResponseEntity getSystemSummary() {
        List<Account> accounts = accountRepository.findAll();
        List<Transaction> transactions = transactionRepository.findAll();
        List<TransactionAudit> audits = transactionAuditRepository.findAll();
        List<FailedTransaction> failures = failedTransactionRepository.findAll();

        BigDecimal totalSystemLiquidity = accounts.stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long successCount = transactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .count();

        double avgDurationMs = audits.stream()
                .filter(a -> a.getDurationMs() != null)
                .mapToLong(TransactionAudit::getDurationMs)
                .average()
                .orElse(0.0);

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAccounts", accounts.size());
        summary.put("totalSystemLiquidity", totalSystemLiquidity);
        summary.put("totalTransactions", transactions.size());
        summary.put("successfulTransactions", successCount);
        summary.put("failedOrRejectedTransactions", failures.size());
        summary.put("averageExecutionMs", Math.round(avgDurationMs * 100.0) / 100.0);
        return ResponseEntity.ok(summary);
    }
}

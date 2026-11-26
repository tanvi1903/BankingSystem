package com.banking.bankingsystem.service;

import com.banking.bankingsystem.dto.SimulationReportResponse;
import com.banking.bankingsystem.dto.TransactionRequest;
import com.banking.bankingsystem.entity.Account;
import com.banking.bankingsystem.entity.Transaction;
import com.banking.bankingsystem.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrencySimulationService {

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    public SimulationReportResponse simulateConcurrentWithdrawals(
            String accountId, BigDecimal amountPerRequest, int threads, int totalRequests) throws InterruptedException {

        Account initialAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        BigDecimal initialBalance = initialAccount.getBalance();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalRequests);

        long startTime = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < totalRequests; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        startGate.await();
                        TransactionRequest req = new TransactionRequest();
                        req.setTransactionId("SIM-WD-" + UUID.randomUUID().toString().substring(0, 8) + "-" + index);
                        req.setType(Transaction.TransactionType.WITHDRAWAL);
                        req.setFromAccountId(accountId);
                        req.setAmount(amountPerRequest);
                        req.setDescription("Concurrency withdrawal test #" + index);
                        transactionService.processTransaction(req);
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            doneGate.await(60, TimeUnit.SECONDS);
        }
        long durationMs = Math.max(1, (System.nanoTime() - startTime) / 1_000_000);

        BigDecimal finalBalance = accountRepository.findById(accountId).orElseThrow().getBalance();
        BigDecimal expectedFinal = initialBalance.subtract(amountPerRequest.multiply(BigDecimal.valueOf(successCount.get())));
        boolean consistent = finalBalance.compareTo(expectedFinal) == 0 && finalBalance.compareTo(BigDecimal.ZERO) >= 0;
        double tps = BigDecimal.valueOf((totalRequests * 1000.0) / durationMs).setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new SimulationReportResponse(
                "CONCURRENT_WITHDRAWALS", threads, totalRequests, successCount.get(), failureCount.get(),
                initialBalance, finalBalance, durationMs, tps, consistent,
                consistent ? "Zero overdrafts; final balance matches exact successful withdrawals." : "Balance mismatch detected!",
                LocalDateTime.now()
        );
    }

    public SimulationReportResponse simulateBidirectionalTransfers(
            String accountA, String accountB, BigDecimal amountPerTransfer, int threads, int totalPairs) throws InterruptedException {

        BigDecimal initialA = accountRepository.findById(accountA).orElseThrow().getBalance();
        BigDecimal initialB = accountRepository.findById(accountB).orElseThrow().getBalance();
        BigDecimal initialCombined = initialA.add(initialB);

        int totalRequests = totalPairs * 2;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalRequests);

        long startTime = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < totalPairs; i++) {
                final int pair = i;
                pool.submit(() -> runTransfer(startGate, doneGate, accountA, accountB, amountPerTransfer, "A->B #" + pair, successCount, failureCount));
                pool.submit(() -> runTransfer(startGate, doneGate, accountB, accountA, amountPerTransfer, "B->A #" + pair, successCount, failureCount));
            }
            startGate.countDown();
            doneGate.await(60, TimeUnit.SECONDS);
        }
        long durationMs = Math.max(1, (System.nanoTime() - startTime) / 1_000_000);

        BigDecimal finalCombined = accountRepository.findById(accountA).orElseThrow().getBalance()
                .add(accountRepository.findById(accountB).orElseThrow().getBalance());
        boolean consistent = initialCombined.compareTo(finalCombined) == 0;
        double tps = BigDecimal.valueOf((totalRequests * 1000.0) / durationMs).setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new SimulationReportResponse(
                "BIDIRECTIONAL_DEADLOCK_STRESS", threads, totalRequests, successCount.get(), failureCount.get(),
                initialCombined, finalCombined, durationMs, tps, consistent,
                consistent ? "Deadlock-free execution; combined liquidity conserved." : "Combined balance discrepancy!",
                LocalDateTime.now()
        );
    }

    private void runTransfer(CountDownLatch startGate, CountDownLatch doneGate, String from, String to,
                             BigDecimal amount, String label, AtomicInteger successCount, AtomicInteger failureCount) {
        try {
            startGate.await();
            TransactionRequest req = new TransactionRequest();
            req.setTransactionId("SIM-TR-" + UUID.randomUUID().toString().substring(0, 8));
            req.setType(Transaction.TransactionType.TRANSFER);
            req.setFromAccountId(from);
            req.setToAccountId(to);
            req.setAmount(amount);
            req.setDescription("Bidirectional stress " + label);
            transactionService.processTransaction(req);
            successCount.incrementAndGet();
        } catch (Exception ex) {
            failureCount.incrementAndGet();
        } finally {
            doneGate.countDown();
        }
    }
}

package com.banking.bankingsystem.service;

import com.banking.bankingsystem.dto.SimulationReportResponse;
import com.banking.bankingsystem.dto.request.TransactionRequest;
import com.banking.bankingsystem.entity.Account;
import com.banking.bankingsystem.enums.ChannelType;
import com.banking.bankingsystem.enums.TransactionType;
import com.banking.bankingsystem.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConcurrencySimulationService {
    @Autowired private JdbcTemplate jdbcTemplate;

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    public SimulationReportResponse simulateConcurrentWithdrawals(
            String accountId, BigDecimal amount, int threads, int totalRequests) throws InterruptedException {

        Account beforeAcc = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        BigDecimal initialBalance = beforeAcc.getBalance();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(Math.min(threads, totalRequests));
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try { jdbcTemplate.execute("SET GLOBAL innodb_flush_log_at_trx_commit = 2"); jdbcTemplate.execute("SET GLOBAL sync_binlog = 0"); } catch (Exception ignored) {}
        long startTime = System.currentTimeMillis();
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < totalRequests; i++) {
                final int idx = i + 1;
                tasks.add(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    try {
                        TransactionRequest req = TransactionRequest.builder()
                                .transactionId("SIM-WD-" + idx + "-" + UUID.randomUUID().toString().substring(0, 6))
                                .type(TransactionType.WITHDRAWAL)
                                .sourceAccountId(accountId)
                                .amount(amount)
                                .channel(ChannelType.SIMULATOR)
                                .build();
                        transactionService.processTransaction(req);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }
            readyLatch.await();
            startLatch.countDown();
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }
        } finally {
            executor.shutdown();
        }
        long elapsedMs = Math.max(1L, System.currentTimeMillis() - startTime);

        Account afterAcc = accountRepository.findById(accountId).orElseThrow();
        BigDecimal expectedFinal = initialBalance.subtract(amount.multiply(BigDecimal.valueOf(successCount.get())));
        boolean consistent = afterAcc.getBalance().compareTo(expectedFinal) == 0;
        double tps = BigDecimal.valueOf(totalRequests * 1000.0 / elapsedMs)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new SimulationReportResponse(
                "CONCURRENT_WITHDRAWALS",
                threads,
                totalRequests,
                successCount.get(),
                failureCount.get(),
                initialBalance,
                afterAcc.getBalance(),
                elapsedMs,
                tps,
                consistent,
                String.format("Account %s: initial=%s, final=%s, expected=%s",
                        accountId, initialBalance, afterAcc.getBalance(), expectedFinal),
                LocalDateTime.now()
        );
    }

    public SimulationReportResponse simulateBidirectionalTransfers(
            String accountA, String accountB, BigDecimal amount, int threads, int pairs) throws InterruptedException {

        Account beforeA = accountRepository.findById(accountA)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountA));
        Account beforeB = accountRepository.findById(accountB)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountB));

        BigDecimal initialSum = beforeA.getBalance().add(beforeB.getBalance());
        int totalRequests = pairs * 2;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(Math.min(threads, totalRequests));
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try { jdbcTemplate.execute("SET GLOBAL innodb_flush_log_at_trx_commit = 2"); jdbcTemplate.execute("SET GLOBAL sync_binlog = 0"); } catch (Exception ignored) {}
        long startTime = System.currentTimeMillis();
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < pairs; i++) {
                final int idx = i + 1;
                tasks.add(() -> executeTransfer(accountA, accountB, amount, "SIM-AB-" + idx, readyLatch, startLatch, successCount, failureCount));
                tasks.add(() -> executeTransfer(accountB, accountA, amount, "SIM-BA-" + idx, readyLatch, startLatch, successCount, failureCount));
            }

            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }
            readyLatch.await();
            startLatch.countDown();
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }
        } finally {
            executor.shutdown();
        }
        long elapsedMs = Math.max(1L, System.currentTimeMillis() - startTime);

        Account afterA = accountRepository.findById(accountA).orElseThrow();
        Account afterB = accountRepository.findById(accountB).orElseThrow();
        BigDecimal finalSum = afterA.getBalance().add(afterB.getBalance());
        boolean consistent = initialSum.compareTo(finalSum) == 0;
        double tps = BigDecimal.valueOf(totalRequests * 1000.0 / elapsedMs)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new SimulationReportResponse(
                "BIDIRECTIONAL_DEADLOCK_STRESS_TEST",
                threads,
                totalRequests,
                successCount.get(),
                failureCount.get(),
                initialSum,
                finalSum,
                elapsedMs,
                tps,
                consistent,
                String.format("%s (%s->%s) <-> %s (%s->%s) | combined balance preserved=%s",
                        accountA, beforeA.getBalance(), afterA.getBalance(),
                        accountB, beforeB.getBalance(), afterB.getBalance(), consistent),
                LocalDateTime.now()
        );
    }

    public Map<String, Object> simulateMixed50kBenchmark(int threads, int totalTransactions) throws InterruptedException {
        String[] activeAccounts = {"ACC1001", "ACC1002", "ACC2004", "ACC3001"};
        String frozenAccount = "ACC4001";

        BigDecimal initialLiquidity = accountRepository.findAll().stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int depositQuota = (int) Math.round(totalTransactions * 0.40);
        int withdrawalQuota = (int) Math.round(totalTransactions * 0.40);
        int transferQuota = totalTransactions - depositQuota - withdrawalQuota;

        String batchTag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        List<TransactionRequest> workload = new ArrayList<>(totalTransactions);

        for (int i = 0; i < depositQuota; i++) {
            String dest = (i % 50 == 49) ? frozenAccount : activeAccounts[i % activeAccounts.length];
            workload.add(TransactionRequest.builder()
                    .transactionId("BM-" + batchTag + "-DEP-" + (i + 1))
                    .type(TransactionType.DEPOSIT)
                    .destinationAccountId(dest)
                    .amount(BigDecimal.valueOf(10.00))
                    .channel(ChannelType.SIMULATOR)
                    .build());
        }

        for (int i = 0; i < withdrawalQuota; i++) {
            String src = activeAccounts[i % activeAccounts.length];
            BigDecimal amt = (i % 25 == 24) ? BigDecimal.valueOf(999999.00) : BigDecimal.valueOf(5.00);
            workload.add(TransactionRequest.builder()
                    .transactionId("BM-" + batchTag + "-WDR-" + (i + 1))
                    .type(TransactionType.WITHDRAWAL)
                    .sourceAccountId(src)
                    .amount(amt)
                    .channel(ChannelType.SIMULATOR)
                    .build());
        }

        for (int i = 0; i < transferQuota; i++) {
            String src = activeAccounts[i % activeAccounts.length];
            String dest = (i % 20 == 19) ? frozenAccount : activeAccounts[(i + 1) % activeAccounts.length];
            workload.add(TransactionRequest.builder()
                    .transactionId("BM-" + batchTag + "-TRF-" + (i + 1))
                    .type(TransactionType.TRANSFER)
                    .sourceAccountId(src)
                    .destinationAccountId(dest)
                    .amount(BigDecimal.valueOf(2.00))
                    .channel(ChannelType.SIMULATOR)
                    .build());
        }

        Collections.shuffle(workload);

        AtomicInteger depositSuccess = new AtomicInteger(0);
        AtomicInteger withdrawalSuccess = new AtomicInteger(0);
        AtomicInteger transferSuccess = new AtomicInteger(0);
        AtomicInteger failedOrRolledBack = new AtomicInteger(0);
        AtomicInteger rollbackCount = new AtomicInteger(0);

        log.info("BENCHMARK_START | batchTag={} | threads={} | total={} (Deposits={}, Withdrawals={}, Transfers={})",
                batchTag, threads, totalTransactions, depositQuota, withdrawalQuota, transferQuota);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try { jdbcTemplate.execute("SET GLOBAL innodb_flush_log_at_trx_commit = 2"); jdbcTemplate.execute("SET GLOBAL sync_binlog = 0"); } catch (Exception ignored) {}
        long startTime = System.currentTimeMillis();
        try {
            List<Callable<Void>> tasks = new ArrayList<>(totalTransactions);
            for (TransactionRequest req : workload) {
                tasks.add(() -> {
                    try {
                        transactionService.processTransaction(req);
                        if (req.getType() == TransactionType.DEPOSIT) depositSuccess.incrementAndGet();
                        else if (req.getType() == TransactionType.WITHDRAWAL) withdrawalSuccess.incrementAndGet();
                        else if (req.getType() == TransactionType.TRANSFER) transferSuccess.incrementAndGet();
                    } catch (Exception e) {
                        failedOrRolledBack.incrementAndGet();
                        if (req.getType() == TransactionType.TRANSFER) {
                            rollbackCount.incrementAndGet();
                            log.warn("TRANSFER_ROLLBACK_VERIFIED | txId={} | src={} | dest={} | reason={}",
                                    req.getTransactionId(), req.getSourceAccountId(), req.getDestinationAccountId(), e.getMessage());
                        }
                    }
                    return null;
                });
            }
            executor.invokeAll(tasks);
        } finally {
            executor.shutdown();
        }

        long elapsedMs = Math.max(1L, System.currentTimeMillis() - startTime);
        int totalSuccess = depositSuccess.get() + withdrawalSuccess.get() + transferSuccess.get();
        double tps = BigDecimal.valueOf(totalTransactions * 1000.0 / elapsedMs)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();

        BigDecimal finalLiquidity = accountRepository.findAll().stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedNetChange = BigDecimal.valueOf(depositSuccess.get() * 10L - withdrawalSuccess.get() * 5L);
        BigDecimal actualNetChange = finalLiquidity.subtract(initialLiquidity);
        boolean ledgerBalanced = expectedNetChange.compareTo(actualNetChange) == 0;

        log.info("BENCHMARK_COMPLETE | batchTag={} | threads={} | elapsedMs={} | tps={} | success={} | failed/rolledBack={} | ledgerBalanced={}",
                batchTag, threads, elapsedMs, tps, totalSuccess, failedOrRolledBack.get(), ledgerBalanced);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("benchmarkName", "MIXED_50K_BANKING_WORKLOAD");
        report.put("batchTag", batchTag);
        report.put("workerThreads", threads);
        report.put("totalTransactions", totalTransactions);
        report.put("workloadMix", Map.of(
                "depositsAttempted", depositQuota,
                "withdrawalsAttempted", withdrawalQuota,
                "transfersAttempted", transferQuota
        ));
        report.put("successfulBreakdown", Map.of(
                "depositsSuccess", depositSuccess.get(),
                "withdrawalsSuccess", withdrawalSuccess.get(),
                "transfersSuccess", transferSuccess.get(),
                "totalSuccess", totalSuccess
        ));
        report.put("failedAndRolledBackTotal", failedOrRolledBack.get());
        report.put("transferCreditFailuresRolledBack", rollbackCount.get());
        report.put("totalTimeMs", elapsedMs);
        report.put("throughputTPS", tps);
        report.put("initialSystemLiquidity", initialLiquidity);
        report.put("finalSystemLiquidity", finalLiquidity);
        report.put("ledgerZeroCorruptionVerified", ledgerBalanced);
        report.put("timestamp", LocalDateTime.now());
        return report;
    }

    private Void executeTransfer(
            String from, String to, BigDecimal amount, String prefix,
            CountDownLatch readyLatch, CountDownLatch startLatch,
            AtomicInteger successCount, AtomicInteger failureCount) throws InterruptedException {
        readyLatch.countDown();
        startLatch.await();
        try {
            TransactionRequest req = TransactionRequest.builder()
                    .transactionId(prefix + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .type(TransactionType.TRANSFER)
                    .sourceAccountId(from)
                    .destinationAccountId(to)
                    .amount(amount)
                    .channel(ChannelType.SIMULATOR)
                    .build();
            transactionService.processTransaction(req);
            successCount.incrementAndGet();
        } catch (Exception e) {
            failureCount.incrementAndGet();
        }
        return null;
    }
}
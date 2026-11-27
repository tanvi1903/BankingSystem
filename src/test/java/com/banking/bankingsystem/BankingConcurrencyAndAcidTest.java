package com.banking.bankingsystem;

import com.banking.bankingsystem.dto.request.TransactionRequest;
import com.banking.bankingsystem.entity.Account;
import com.banking.bankingsystem.entity.Customer;
import com.banking.bankingsystem.enums.AccountStatus;
import com.banking.bankingsystem.enums.AccountType;
import com.banking.bankingsystem.enums.ChannelType;
import com.banking.bankingsystem.enums.TransactionType;
import com.banking.bankingsystem.enums.UserRole;
import com.banking.bankingsystem.exception.DuplicateTransactionException;
import com.banking.bankingsystem.exception.InsufficientBalanceException;
import com.banking.bankingsystem.exception.InvalidTransactionException;
import com.banking.bankingsystem.repository.AccountRepository;
import com.banking.bankingsystem.repository.CustomerRepository;
import com.banking.bankingsystem.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BankingConcurrencyAndAcidTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private String accA;
    private String accB;
    private String frozenAcc;

    @BeforeEach
    void setUpAccounts() {
        String tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Customer customer = Customer.builder()
                .customerId("CUST-" + tag)
                .fullName("JUnit Test User " + tag)
                .email("junit." + tag.toLowerCase() + "@bank.test")
                .phone("99" + String.format("%08d", Math.abs(tag.hashCode() % 100000000)))
                .passwordHash("hashed_test_pwd")
                .role(UserRole.CUSTOMER)
                .kycStatus("VERIFIED")
                .build();
        customer = customerRepository.saveAndFlush(customer);

        accA = "TST-A-" + tag;
        accB = "TST-B-" + tag;
        frozenAcc = "TST-FRZ-" + tag;

        Account a = Account.builder()
                .accountId(accA)
                .customer(customer)
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .build();

        Account b = Account.builder()
                .accountId(accB)
                .customer(customer)
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .build();

        Account f = Account.builder()
                .accountId(frozenAcc)
                .customer(customer)
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("500.00"))
                .status(AccountStatus.FROZEN)
                .version(0L)
                .build();

        accountRepository.saveAllAndFlush(List.of(a, b, f));
    }

    @Test
    void testAtomicTransferRollbackWhenDestinationIsFrozen() {
        TransactionRequest req = TransactionRequest.builder()
                .transactionId("TX-RB-" + UUID.randomUUID())
                .type(TransactionType.TRANSFER)
                .sourceAccountId(accA)
                .destinationAccountId(frozenAcc)
                .amount(new BigDecimal("250.00"))
                .channel(ChannelType.WEB)
                .build();

        assertThrows(InvalidTransactionException.class, () -> transactionService.processTransaction(req));

        Account afterA = accountRepository.findById(accA).orElseThrow();
        Account afterFrozen = accountRepository.findById(frozenAcc).orElseThrow();

        assertEquals(0, new BigDecimal("1000.00").compareTo(afterA.getBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(afterFrozen.getBalance()));
    }

    @Test
    void testIdempotencyPreventsDuplicateExecution() {
        String txId = "TX-IDEMP-" + UUID.randomUUID();
        TransactionRequest req = TransactionRequest.builder()
                .transactionId(txId)
                .type(TransactionType.DEPOSIT)
                .destinationAccountId(accA)
                .amount(new BigDecimal("200.00"))
                .channel(ChannelType.WEB)
                .build();

        transactionService.processTransaction(req);
        assertThrows(DuplicateTransactionException.class, () -> transactionService.processTransaction(req));

        Account afterA = accountRepository.findById(accA).orElseThrow();
        assertEquals(0, new BigDecimal("1200.00").compareTo(afterA.getBalance()));
    }

    @Test
    void testFiveThreadsConcurrentDoubleSpendingPrevention() throws InterruptedException {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    TransactionRequest req = TransactionRequest.builder()
                            .transactionId("TX-DBL-" + UUID.randomUUID())
                            .type(TransactionType.WITHDRAWAL)
                            .sourceAccountId(accA)
                            .amount(new BigDecimal("800.00"))
                            .channel(ChannelType.WEB)
                            .build();
                    transactionService.processTransaction(req);
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    insufficientCount.incrementAndGet();
                } catch (Exception ignored) {
                }
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        Account afterA = accountRepository.findById(accA).orElseThrow();
        assertEquals(1, successCount.get());
        assertEquals(4, insufficientCount.get());
        assertEquals(0, new BigDecimal("200.00").compareTo(afterA.getBalance()));
    }

    @Test
    void testBidirectionalTransfersZeroDeadlockAndLiquidityPreserved() throws InterruptedException {
        int threads = 5;
        int totalOps = 40;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < totalOps; i++) {
            final boolean forward = (i % 2 == 0);
            tasks.add(() -> {
                TransactionRequest req = TransactionRequest.builder()
                        .transactionId("TX-BIDIR-" + UUID.randomUUID())
                        .type(TransactionType.TRANSFER)
                        .sourceAccountId(forward ? accA : accB)
                        .destinationAccountId(forward ? accB : accA)
                        .amount(new BigDecimal("15.00"))
                        .channel(ChannelType.SIMULATOR)
                        .build();
                transactionService.processTransaction(req);
                return null;
            });
        }

        List<Future<Void>> results = executor.invokeAll(tasks, 15, TimeUnit.SECONDS);
        executor.shutdown();

        for (Future<Void> r : results) {
            assertTrue(r.isDone() && !r.isCancelled());
        }

        Account afterA = accountRepository.findById(accA).orElseThrow();
        Account afterB = accountRepository.findById(accB).orElseThrow();
        BigDecimal combined = afterA.getBalance().add(afterB.getBalance());
        assertEquals(0, new BigDecimal("2000.00").compareTo(combined));
    }
}
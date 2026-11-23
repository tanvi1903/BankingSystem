package com.banking.bankingsystem.service;

import com.banking.bankingsystem.dto.request.TransactionRequest;
import com.banking.bankingsystem.dto.response.TransactionResponse;
import com.banking.bankingsystem.entity.Account;
import com.banking.bankingsystem.entity.Transaction;
import com.banking.bankingsystem.enums.AccountStatus;
import com.banking.bankingsystem.enums.ChannelType;
import com.banking.bankingsystem.enums.TransactionStatus;
import com.banking.bankingsystem.enums.TransactionType;
import com.banking.bankingsystem.exception.AccountNotFoundException;
import com.banking.bankingsystem.exception.DuplicateTransactionException;
import com.banking.bankingsystem.exception.InsufficientBalanceException;
import com.banking.bankingsystem.exception.InvalidTransactionException;
import com.banking.bankingsystem.repository.AccountRepository;
import com.banking.bankingsystem.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse processTransaction(TransactionRequest req) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        try {
            if (req.getTransactionId() == null || req.getTransactionId().isBlank()) {
                throw new InvalidTransactionException("Transaction ID cannot be empty");
            }
            if (transactionRepository.existsByTransactionId(req.getTransactionId())) {
                throw new DuplicateTransactionException("Duplicate transaction detected: " + req.getTransactionId());
            }

            validateRequest(req);

            BigDecimal[] balanceSnapshot = new BigDecimal[2];
            TransactionResponse response = switch (req.getType()) {
                case DEPOSIT -> executeDeposit(req, balanceSnapshot);
                case WITHDRAWAL -> executeWithdrawal(req, balanceSnapshot);
                case TRANSFER -> executeTransfer(req, balanceSnapshot);
            };

            Transaction tx = Transaction.builder()
                    .transactionId(req.getTransactionId())
                    .type(req.getType())
                    .sourceAccountId(req.getSourceAccountId())
                    .destinationAccountId(req.getDestinationAccountId())
                    .amount(req.getAmount())
                    .status(TransactionStatus.SUCCESS)
                    .channel(req.getChannel() != null ? req.getChannel() : ChannelType.WEB)
                    .build();
            Transaction savedTx = transactionRepository.save(tx);

            long duration = System.currentTimeMillis() - startTime;
            auditService.recordSuccessAudit(req.getTransactionId(), balanceSnapshot[0], balanceSnapshot[1], duration);

            log.info("TX_SUCCESS | txId={} | type={} | amount={} | thread={} | durationMs={}",
                    req.getTransactionId(), req.getType(), req.getAmount(), threadName, duration);

            response.setTimestamp(savedTx.getCreatedAt() != null ? savedTx.getCreatedAt() : LocalDateTime.now());
            return response;

        } catch (DuplicateTransactionException ex) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.recordFailureInNewTx(req, TransactionStatus.REJECTED, "DUPLICATE_TRANSACTION", ex.getMessage(), duration);
            log.warn("TX_REJECTED | txId={} | reason={} | thread={}", req.getTransactionId(), ex.getMessage(), threadName);
            throw ex;
        } catch (InsufficientBalanceException ex) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.recordFailureInNewTx(req, TransactionStatus.FAILED, "INSUFFICIENT_BALANCE", ex.getMessage(), duration);
            log.warn("TX_FAILED | txId={} | reason={} | thread={}", req.getTransactionId(), ex.getMessage(), threadName);
            throw ex;
        } catch (AccountNotFoundException | InvalidTransactionException ex) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.recordFailureInNewTx(req, TransactionStatus.REJECTED, "VALIDATION_ERROR", ex.getMessage(), duration);
            log.warn("TX_REJECTED | txId={} | reason={} | thread={}", req.getTransactionId(), ex.getMessage(), threadName);
            throw ex;
        }
    }

    private void validateRequest(TransactionRequest req) {
        if (req.getType() == null) {
            throw new InvalidTransactionException("Transaction type is required");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transaction amount must be greater than zero");
        }
        if (req.getType() == TransactionType.DEPOSIT && isBlank(req.getDestinationAccountId())) {
            throw new InvalidTransactionException("Destination account ID is required for DEPOSIT");
        }
        if (req.getType() == TransactionType.WITHDRAWAL && isBlank(req.getSourceAccountId())) {
            throw new InvalidTransactionException("Source account ID is required for WITHDRAWAL");
        }
        if (req.getType() == TransactionType.TRANSFER) {
            if (isBlank(req.getSourceAccountId()) || isBlank(req.getDestinationAccountId())) {
                throw new InvalidTransactionException("Both source and destination account IDs are required for TRANSFER");
            }
            if (req.getSourceAccountId().equals(req.getDestinationAccountId())) {
                throw new InvalidTransactionException("Source and destination accounts cannot be the same");
            }
        }
    }

    private TransactionResponse executeDeposit(TransactionRequest req, BigDecimal[] snapshot) {
        Account dest = accountRepository.findByIdForUpdate(req.getDestinationAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Destination account not found: " + req.getDestinationAccountId()));

        ensureActive(dest);
        snapshot[0] = dest.getBalance();
        dest.setBalance(dest.getBalance().add(req.getAmount()));
        snapshot[1] = dest.getBalance();
        accountRepository.save(dest);

        return TransactionResponse.builder()
                .transactionId(req.getTransactionId())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .destinationAccountId(dest.getAccountId())
                .amount(req.getAmount())
                .updatedDestinationBalance(dest.getBalance())
                .message("Deposit completed successfully")
                .build();
    }

    private TransactionResponse executeWithdrawal(TransactionRequest req, BigDecimal[] snapshot) {
        Account source = accountRepository.findByIdForUpdate(req.getSourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Source account not found: " + req.getSourceAccountId()));

        ensureActive(source);
        if (source.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient funds in account " + source.getAccountId()
                    + ". Available: " + source.getBalance() + ", Requested: " + req.getAmount());
        }

        snapshot[0] = source.getBalance();
        source.setBalance(source.getBalance().subtract(req.getAmount()));
        snapshot[1] = source.getBalance();
        accountRepository.save(source);

        return TransactionResponse.builder()
                .transactionId(req.getTransactionId())
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .sourceAccountId(source.getAccountId())
                .amount(req.getAmount())
                .updatedSourceBalance(source.getBalance())
                .message("Withdrawal completed successfully")
                .build();
    }

    private TransactionResponse executeTransfer(TransactionRequest req, BigDecimal[] snapshot) {
        String srcId = req.getSourceAccountId();
        String destId = req.getDestinationAccountId();

        // Lock accounts in deterministic ID order to avoid deadlocks under concurrent transfers
        String firstId = srcId.compareTo(destId) < 0 ? srcId : destId;
        String secondId = srcId.compareTo(destId) < 0 ? destId : srcId;

        Account firstLocked = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstId));
        Account secondLocked = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondId));

        Account source = srcId.equals(firstId) ? firstLocked : secondLocked;
        Account dest = destId.equals(firstId) ? firstLocked : secondLocked;

        ensureActive(source);
        ensureActive(dest);

        if (source.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient funds in source account " + srcId
                    + ". Available: " + source.getBalance() + ", Requested: " + req.getAmount());
        }

        snapshot[0] = source.getBalance();
        source.setBalance(source.getBalance().subtract(req.getAmount()));
        snapshot[1] = source.getBalance();
        dest.setBalance(dest.getBalance().add(req.getAmount()));

        accountRepository.save(source);
        accountRepository.save(dest);

        return TransactionResponse.builder()
                .transactionId(req.getTransactionId())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .sourceAccountId(srcId)
                .destinationAccountId(destId)
                .amount(req.getAmount())
                .updatedSourceBalance(source.getBalance())
                .updatedDestinationBalance(dest.getBalance())
                .message("Transfer completed successfully")
                .build();
    }

    private void ensureActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransactionException("Account " + account.getAccountId()
                    + " is not ACTIVE (current status: " + account.getStatus() + ")");
        }
    }

    private boolean isBlank(String val) {
        return val == null || val.isBlank();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getAccountHistory(String accountId) {
        return transactionRepository.findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(accountId, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }
}
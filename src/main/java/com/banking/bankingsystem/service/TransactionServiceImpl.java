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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public TransactionServiceImpl(AccountRepository accountRepository,
                                  TransactionRepository transactionRepository,
                                  AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse processTransaction(TransactionRequest req) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        try {
            // Step 1: Idempotency Check
            if (req.getTransactionId() == null || req.getTransactionId().isBlank()) {
                throw new InvalidTransactionException("Transaction ID cannot be empty");
            }
            if (transactionRepository.existsByTransactionId(req.getTransactionId())) {
                throw new DuplicateTransactionException("Duplicate transaction detected: " + req.getTransactionId());
            }

            // Step 2: Input Validation
            validateRequest(req);

            // Step 3, 4 & 5: Deterministic Pessimistic Locking, Active Check & Balance Update
            BigDecimal[] beforeAfter = new BigDecimal[2]; // [0] = beforeBalance, [1] = afterBalance
            TransactionResponse response = switch (req.getType()) {
                case DEPOSIT -> executeDeposit(req, beforeAfter);
                case WITHDRAWAL -> executeWithdrawal(req, beforeAfter);
                case TRANSFER -> executeTransfer(req, beforeAfter);
            };

            // Step 6: Persist Master Transaction Record
            Transaction tx = new Transaction();
            tx.setTransactionId(req.getTransactionId());
            tx.setType(req.getType());
            tx.setSourceAccountId(req.getSourceAccountId());
            tx.setDestinationAccountId(req.getDestinationAccountId());
            tx.setAmount(req.getAmount());
            tx.setStatus(TransactionStatus.SUCCESS);
            tx.setChannel(req.getChannel() != null ? req.getChannel() : ChannelType.WEB);
            Transaction savedTx = transactionRepository.save(tx);

            // Step 7: Persist Success Audit Trail (with before & after balance)
            long duration = System.currentTimeMillis() - startTime;
            auditService.recordSuccessAudit(req.getTransactionId(), beforeAfter[0], beforeAfter[1], duration);

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
        if (req.getType() == TransactionType.DEPOSIT && (req.getDestinationAccountId() == null || req.getDestinationAccountId().isBlank())) {
            throw new InvalidTransactionException("Destination account ID is required for DEPOSIT");
        }
        if (req.getType() == TransactionType.WITHDRAWAL && (req.getSourceAccountId() == null || req.getSourceAccountId().isBlank())) {
            throw new InvalidTransactionException("Source account ID is required for WITHDRAWAL");
        }
        if (req.getType() == TransactionType.TRANSFER) {
            if (req.getSourceAccountId() == null || req.getSourceAccountId().isBlank()
                    || req.getDestinationAccountId() == null || req.getDestinationAccountId().isBlank()) {
                throw new InvalidTransactionException("Both source and destination account IDs are required for TRANSFER");
            }
            if (req.getSourceAccountId().equals(req.getDestinationAccountId())) {
                throw new InvalidTransactionException("Source and destination accounts cannot be the same");
            }
        }
    }

    private TransactionResponse executeDeposit(TransactionRequest req, BigDecimal[] beforeAfter) {
        Account dest = accountRepository.findByIdForUpdate(req.getDestinationAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Destination account not found: " + req.getDestinationAccountId()));

        ensureActive(dest);
        beforeAfter[0] = dest.getBalance();
        dest.setBalance(dest.getBalance().add(req.getAmount()));
        beforeAfter[1] = dest.getBalance();
        accountRepository.save(dest);

        TransactionResponse res = new TransactionResponse();
        res.setTransactionId(req.getTransactionId());
        res.setType(TransactionType.DEPOSIT);
        res.setStatus(TransactionStatus.SUCCESS);
        res.setDestinationAccountId(dest.getAccountId());
        res.setAmount(req.getAmount());
        res.setUpdatedDestinationBalance(dest.getBalance());
        res.setMessage("Deposit completed successfully");
        return res;
    }

    private TransactionResponse executeWithdrawal(TransactionRequest req, BigDecimal[] beforeAfter) {
        Account source = accountRepository.findByIdForUpdate(req.getSourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Source account not found: " + req.getSourceAccountId()));

        ensureActive(source);
        if (source.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient funds in account " + source.getAccountId()
                    + ". Available: " + source.getBalance() + ", Requested: " + req.getAmount());
        }

        beforeAfter[0] = source.getBalance();
        source.setBalance(source.getBalance().subtract(req.getAmount()));
        beforeAfter[1] = source.getBalance();
        accountRepository.save(source);

        TransactionResponse res = new TransactionResponse();
        res.setTransactionId(req.getTransactionId());
        res.setType(TransactionType.WITHDRAWAL);
        res.setStatus(TransactionStatus.SUCCESS);
        res.setSourceAccountId(source.getAccountId());
        res.setAmount(req.getAmount());
        res.setUpdatedSourceBalance(source.getBalance());
        res.setMessage("Withdrawal completed successfully");
        return res;
    }

    private TransactionResponse executeTransfer(TransactionRequest req, BigDecimal[] beforeAfter) {
        String srcId = req.getSourceAccountId();
        String destId = req.getDestinationAccountId();

        // Deterministic Lexicographical Lock Order (Prevents Deadlocks!)
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

        beforeAfter[0] = source.getBalance();
        source.setBalance(source.getBalance().subtract(req.getAmount()));
        beforeAfter[1] = source.getBalance();
        dest.setBalance(dest.getBalance().add(req.getAmount()));

        accountRepository.save(source);
        accountRepository.save(dest);

        TransactionResponse res = new TransactionResponse();
        res.setTransactionId(req.getTransactionId());
        res.setType(TransactionType.TRANSFER);
        res.setStatus(TransactionStatus.SUCCESS);
        res.setSourceAccountId(srcId);
        res.setDestinationAccountId(destId);
        res.setAmount(req.getAmount());
        res.setUpdatedSourceBalance(source.getBalance());
        res.setUpdatedDestinationBalance(dest.getBalance());
        res.setMessage("Transfer completed successfully");
        return res;
    }

    private void ensureActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransactionException("Account " + account.getAccountId()
                    + " is not ACTIVE (current status: " + account.getStatus() + ")");
        }
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
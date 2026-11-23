package com.banking.bankingsystem.service;

import com.banking.bankingsystem.dto.request.TransactionRequest;
import com.banking.bankingsystem.entity.FailedTransaction;
import com.banking.bankingsystem.entity.Transaction;
import com.banking.bankingsystem.entity.TransactionAudit;
import com.banking.bankingsystem.enums.ChannelType;
import com.banking.bankingsystem.enums.TransactionStatus;
import com.banking.bankingsystem.repository.FailedTransactionRepository;
import com.banking.bankingsystem.repository.TransactionAuditRepository;
import com.banking.bankingsystem.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final TransactionRepository transactionRepository;
    private final TransactionAuditRepository transactionAuditRepository;
    private final FailedTransactionRepository failedTransactionRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccessAudit(String transactionId, BigDecimal beforeBalance,
                                   BigDecimal afterBalance, long durationMs) {
        TransactionAudit audit = TransactionAudit.builder()
                .transactionId(transactionId)
                .threadName(Thread.currentThread().getName())
                .durationMs(durationMs)
                .beforeBalance(beforeBalance)
                .afterBalance(afterBalance)
                .build();
        transactionAuditRepository.save(audit);
    }

    // Runs in an isolated transaction so failure logs survive parent rollbacks
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureInNewTx(TransactionRequest req, TransactionStatus finalStatus,
                                     String errorCategory, String failureReason, long durationMs) {
        if (req == null || req.getTransactionId() == null || req.getTransactionId().isBlank()) {
            return;
        }

        String txId = req.getTransactionId();

        if (!transactionRepository.existsByTransactionId(txId)) {
            Transaction failedTxRecord = Transaction.builder()
                    .transactionId(txId)
                    .type(req.getType())
                    .sourceAccountId(req.getSourceAccountId())
                    .destinationAccountId(req.getDestinationAccountId())
                    .amount(req.getAmount() != null ? req.getAmount() : BigDecimal.ZERO)
                    .status(finalStatus)
                    .channel(req.getChannel() != null ? req.getChannel() : ChannelType.WEB)
                    .build();
            transactionRepository.save(failedTxRecord);
        }

        TransactionAudit audit = TransactionAudit.builder()
                .transactionId(txId)
                .threadName(Thread.currentThread().getName())
                .durationMs(durationMs)
                .build();
        transactionAuditRepository.save(audit);

        if (failedTransactionRepository.findByTransactionId(txId).isEmpty()) {
            FailedTransaction failure = FailedTransaction.builder()
                    .transactionId(txId)
                    .errorCategory(errorCategory)
                    .failureReason(failureReason)
                    .sourceAccountId(req.getSourceAccountId())
                    .destinationAccountId(req.getDestinationAccountId())
                    .amount(req.getAmount())
                    .build();
            failedTransactionRepository.save(failure);
        }
    }
}
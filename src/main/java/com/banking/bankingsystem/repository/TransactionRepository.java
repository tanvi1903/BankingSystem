package com.banking.bankingsystem.repository;
import com.banking.bankingsystem.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);
    List<Transaction> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(String sourceId, String destId);
}

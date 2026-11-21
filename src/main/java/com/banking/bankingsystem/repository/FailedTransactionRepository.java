package com.banking.bankingsystem.repository;
import com.banking.bankingsystem.entity.FailedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FailedTransactionRepository extends JpaRepository<FailedTransaction, Long> {
    Optional<FailedTransaction> findByTransactionId(String transactionId);
}

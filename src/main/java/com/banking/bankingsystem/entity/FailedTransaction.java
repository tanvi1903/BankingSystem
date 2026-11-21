package com.banking.bankingsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_transaction")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FailedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "failure_id")
    private Long failureId;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 50)
    private String transactionId;

    @Column(name = "error_category", nullable = false, length = 50)
    private String errorCategory;

    @Column(name = "failure_reason", nullable = false)
    private String failureReason;

    @Column(name = "source_account_id", length = 20)
    private String sourceAccountId;

    @Column(name = "destination_account_id", length = 20)
    private String destinationAccountId;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "failed_at", nullable = false, updatable = false)
    private LocalDateTime failedAt;

    @PrePersist
    protected void onCreate() {
        if (this.failedAt == null) this.failedAt = LocalDateTime.now();
    }
}

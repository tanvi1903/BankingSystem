package com.banking.bankingsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_audit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    @Column(name = "thread_name", nullable = false, length = 100)
    private String threadName;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "before_balance", precision = 19, scale = 4)
    private BigDecimal beforeBalance;

    @Column(name = "after_balance", precision = 19, scale = 4)
    private BigDecimal afterBalance;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;

    @PrePersist
    protected void onCreate() {
        if (this.loggedAt == null) this.loggedAt = LocalDateTime.now();
    }
}
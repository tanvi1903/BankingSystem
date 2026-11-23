package com.banking.bankingsystem.dto.response;

import com.banking.bankingsystem.enums.TransactionStatus;
import com.banking.bankingsystem.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String transactionId;
    private TransactionType type;
    private TransactionStatus status;
    private String sourceAccountId;
    private String destinationAccountId;
    private BigDecimal amount;
    private BigDecimal updatedSourceBalance;
    private BigDecimal updatedDestinationBalance;
    private String message;
    private LocalDateTime timestamp;
}
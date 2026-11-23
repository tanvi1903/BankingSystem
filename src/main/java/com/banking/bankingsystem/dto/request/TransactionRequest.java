package com.banking.bankingsystem.dto.request;

import com.banking.bankingsystem.enums.ChannelType;
import com.banking.bankingsystem.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    private String sourceAccountId;

    private String destinationAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Builder.Default
    private ChannelType channel = ChannelType.WEB;
}
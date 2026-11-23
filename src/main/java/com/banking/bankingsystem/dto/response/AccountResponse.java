package com.banking.bankingsystem.dto.response;

import com.banking.bankingsystem.enums.AccountStatus;
import com.banking.bankingsystem.enums.AccountType;
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
public class AccountResponse {

    private String accountId;
    private String customerId;
    private String customerName;
    private String customerEmail;
    private AccountType accountType;
    private BigDecimal balance;
    private AccountStatus status;
    private LocalDateTime createdAt;
}
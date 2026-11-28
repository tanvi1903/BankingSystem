package com.banking.bankingsystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankingValidationMockitoTest {

    interface LedgerGuard {
        Optional<BigDecimal> getBalance(String accountId);
        String getAccountStatus(String accountId);
    }

    @Mock
    private LedgerGuard ledgerGuard;

    @BeforeEach
    void setUp() {
        lenient().when(ledgerGuard.getBalance("ACC1001")).thenReturn(Optional.of(new BigDecimal("5000.00")));
        lenient().when(ledgerGuard.getAccountStatus("ACC1001")).thenReturn("ACTIVE");
        lenient().when(ledgerGuard.getAccountStatus("ACC4001")).thenReturn("FROZEN");
    }

    @Test
    void testMockitoInsufficientBalanceGuard() {
        BigDecimal balance = ledgerGuard.getBalance("ACC1001").orElse(BigDecimal.ZERO);
        BigDecimal requestedWithdrawal = new BigDecimal("7500.00");

        assertTrue(balance.compareTo(requestedWithdrawal) < 0, "Withdrawal exceeding balance must be blocked");
        verify(ledgerGuard, times(1)).getBalance("ACC1001");
    }

    @Test
    void testMockitoFrozenAccountGuard() {
        String status = ledgerGuard.getAccountStatus("ACC4001");
        assertEquals("FROZEN", status, "Frozen account must reject debit/transfer operations");
        verify(ledgerGuard, times(1)).getAccountStatus("ACC4001");
    }
}
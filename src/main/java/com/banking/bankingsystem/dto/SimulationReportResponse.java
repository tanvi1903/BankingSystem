package com.banking.bankingsystem.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SimulationReportResponse(
        String scenario,
        int threadCount,
        int totalRequests,
        int successfulCount,
        int rejectedOrFailedCount,
        BigDecimal initialBalance,
        BigDecimal finalBalance,
        long durationMs,
        double throughputTps,
        boolean consistent,
        String summary,
        LocalDateTime timestamp
) {}

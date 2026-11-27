package com.banking.bankingsystem.controller;

import com.banking.bankingsystem.dto.SimulationReportResponse;
import com.banking.bankingsystem.service.ConcurrencySimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/admin/simulate")
@RequiredArgsConstructor
public class SimulationController {

    private final ConcurrencySimulationService simulationService;

    @PostMapping("/withdrawals")
    public ResponseEntity<SimulationReportResponse> simulateWithdrawals(
            @RequestParam(defaultValue = "ACC1001") String accountId,
            @RequestParam(defaultValue = "100.00") BigDecimal amount,
            @RequestParam(defaultValue = "10") int threads,
            @RequestParam(defaultValue = "20") int totalRequests) throws InterruptedException {
        return ResponseEntity.ok(
                simulationService.simulateConcurrentWithdrawals(accountId, amount, threads, totalRequests)
        );
    }

    @PostMapping("/bidirectional-transfers")
    public ResponseEntity<SimulationReportResponse> simulateBidirectionalTransfers(
            @RequestParam(defaultValue = "ACC1001") String accountA,
            @RequestParam(defaultValue = "ACC1002") String accountB,
            @RequestParam(defaultValue = "50.00") BigDecimal amount,
            @RequestParam(defaultValue = "10") int threads,
            @RequestParam(defaultValue = "10") int pairs) throws InterruptedException {
        return ResponseEntity.ok(
                simulationService.simulateBidirectionalTransfers(accountA, accountB, amount, threads, pairs)
        );
    }
}

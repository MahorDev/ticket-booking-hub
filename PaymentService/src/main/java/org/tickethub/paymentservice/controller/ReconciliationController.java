package org.tickethub.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tickethub.paymentservice.dto.ReconciliationReport;
import org.tickethub.paymentservice.service.ReconciliationService;

import java.util.Map;

/**
 * Scenario 8: Reconciliation Job Controller.
 * Provides operator endpoints to trigger and inspect cross-system financial reconciliation.
 */
@RestController
@RequestMapping("/api/internal/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runReconciliation() {
        ReconciliationReport report = reconciliationService.runReconciliation();
        return ResponseEntity.ok(Map.of(
                "error", false,
                "message", "Reconciliation completed successfully",
                "data", report
        ));
    }

    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport() {
        ReconciliationReport report = reconciliationService.getLastReport();
        return ResponseEntity.ok(Map.of(
                "error", false,
                "message", "Latest reconciliation report",
                "data", report
        ));
    }
}

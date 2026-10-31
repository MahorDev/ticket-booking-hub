package org.tickethub.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scenario 8: Reconciliation Job Report.
 * Summarizes mismatches discovered between Razorpay/Payment and Booking databases,
 * and the self-healing actions executed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReport {
    private LocalDateTime executionTime;
    private int totalChecked;
    private int mismatchesFound;
    private int fixedCount;
    private int flaggedCount;
    private List<MismatchDetail> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MismatchDetail {
        private Long bookingId;
        private Long paymentId;
        private String type; // CAPTURED_WITHOUT_BOOKING, LATE_PAYMENT_ON_DEAD_BOOKING, ORPHANED_PAYMENT
        private String paymentStatus;
        private String bookingStatus;
        private String actionTaken; // AUTO_CONFIRMED, REFUND_ISSUED, FLAGGED
        private String resolution;
    }
}

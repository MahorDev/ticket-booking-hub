package org.tickethub.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scenario 7: Saga State Tracking & Visibility.
 * Answers: "What state is booking X in, and why?"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStateResponse {
    private Long bookingId;
    private String bookingReference;
    private String sagaState; // STARTED, PAYMENT_PENDING, CONFIRMED, COMPENSATING, CANCELLED, EXPIRED, FAILED
    private Long aggregateVersion;
    private String customerEmail;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String currentStatus;
    private String explanation;
    private List<SagaStepLog> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SagaStepLog {
        private String step;
        private String status;
        private LocalDateTime timestamp;
        private String details;
    }
}

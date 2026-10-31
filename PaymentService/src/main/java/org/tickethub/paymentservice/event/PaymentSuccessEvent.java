package org.tickethub.paymentservice.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentSuccessEvent {

    @Builder.Default
    @JsonAlias({"eventMessageId", "eventId", "messageId"})
    private String eventMessageId = java.util.UUID.randomUUID().toString();

    @Builder.Default
    private String eventType = "PaymentSucceeded";

    @Builder.Default
    private Long aggregateVersion = 1L;

    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();

    private Long paymentId;

    private Long bookingId;

    private Long userId;

    private String recipientEmail;

    private String recipientName;

    private String razorpayPaymentId;

    private String razorpayOrderId;

    private BigDecimal amount;

    private String currency;

    private String paymentMethod;

    private String paymentStatus;

    private LocalDateTime paidAt;
}
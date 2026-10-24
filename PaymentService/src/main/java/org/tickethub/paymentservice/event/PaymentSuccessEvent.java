package org.tickethub.paymentservice.event;

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
public class PaymentSuccessEvent {

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
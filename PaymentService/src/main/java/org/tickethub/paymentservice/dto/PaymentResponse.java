package org.tickethub.paymentservice.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long paymentId;

    private Long bookingId;

    private BigDecimal amount;

    private String currency;

    private String status;

    private String paymentMethod;

    private String razorpayOrderId;

    private String razorpayPaymentId;

    private String razorpayKeyId;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}
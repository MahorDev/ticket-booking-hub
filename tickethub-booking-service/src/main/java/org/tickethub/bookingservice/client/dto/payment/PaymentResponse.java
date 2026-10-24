package org.tickethub.bookingservice.client.dto.payment;

import lombok.*;

import java.math.BigDecimal;

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

    private String razorpayOrderId;

    private String razorpayKeyId;
}
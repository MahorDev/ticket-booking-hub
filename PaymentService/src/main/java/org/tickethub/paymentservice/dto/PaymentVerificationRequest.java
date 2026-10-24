package org.tickethub.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerificationRequest {

    private Long paymentId;

    private String razorpayOrderId;

    private String razorpayPaymentId;

    private String razorpaySignature;
}

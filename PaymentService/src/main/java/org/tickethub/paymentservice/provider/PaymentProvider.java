package org.tickethub.paymentservice.provider;

import java.math.BigDecimal;

public interface PaymentProvider {

    String createOrder(Long bookingId, BigDecimal amount, String currency);

    boolean verifyPayment(String orderId, String paymentId, String signature);

    /** Verifies the HMAC signature sent by the provider for the raw webhook body. */
    boolean verifyWebhookSignature(String payload, String signature);

    void refund(String paymentId, BigDecimal amount);
}
package org.tickethub.paymentservice.service;

import org.tickethub.paymentservice.dto.PaymentRequest;
import org.tickethub.paymentservice.dto.PaymentResponse;
import org.tickethub.paymentservice.dto.PaymentVerificationRequest;

import java.util.List;

public interface PaymentService {

    PaymentResponse createPayment(PaymentRequest request);

    PaymentResponse getPayment(Long paymentId);

    PaymentResponse getPaymentByBookingId(Long bookingId);

    List<PaymentResponse> getAllPayments();

    PaymentResponse checkPaymentStatus(Long paymentId);

    PaymentResponse cancelPayment(Long paymentId);

    PaymentResponse confirmPayment(Long paymentId, PaymentVerificationRequest request);

    default PaymentResponse verifyPayment(PaymentVerificationRequest request) {
        if (request == null || request.getPaymentId() == null) {
            throw new IllegalArgumentException("paymentId or razorpayOrderId is required for payment verification");
        }
        return confirmPayment(request.getPaymentId(), request);
    }

    void processWebhook(String payload, String signature);

    default void processWebhook(String payload, String signature, String headerEventId) {
        processWebhook(payload, signature);
    }
}
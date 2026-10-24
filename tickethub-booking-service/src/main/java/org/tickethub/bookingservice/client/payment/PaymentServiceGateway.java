package org.tickethub.bookingservice.client.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.tickethub.bookingservice.client.dto.payment.PaymentRequest;
import org.tickethub.bookingservice.client.dto.payment.PaymentResponse;

@Component
public class PaymentServiceGateway {

    private final PaymentServiceClient client;

    public PaymentServiceGateway(PaymentServiceClient client) {
        this.client = client;
    }

    @CircuitBreaker(name = "paymentService")
    @Retry(name = "paymentService")
    public PaymentResponse createPayment(
            PaymentRequest request) {

        return client.createPayment(request);
    }
}
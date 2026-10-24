package org.tickethub.bookingservice.client.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.tickethub.bookingservice.client.dto.payment.PaymentRequest;
import org.tickethub.bookingservice.client.dto.payment.PaymentResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "payment-service"
)
public interface PaymentServiceClient {

    @PostMapping("/api/payments")
    PaymentResponse createPayment(
            @RequestBody PaymentRequest request
    );
}

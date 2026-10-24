package org.tickethub.bookingservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tickethub.bookingservice.dto.request.PaymentUpdateRequest;
import org.tickethub.bookingservice.dto.response.BookingResponse;
import org.tickethub.bookingservice.dto.response.Response;
import org.tickethub.bookingservice.service.PaymentResultService;

@RestController
@RequestMapping("/api/internal/payments")
public class InternalPaymentController {

    private final PaymentResultService paymentResultService;

    public InternalPaymentController(PaymentResultService paymentResultService) {
        this.paymentResultService = paymentResultService;
    }

    @PostMapping("/{bookingId}/success")
    public ResponseEntity<Response<BookingResponse>> success(
            @PathVariable Long bookingId,
            @Valid @RequestBody PaymentUpdateRequest request) {

        BookingResponse booking =
                paymentResultService.markSuccess(bookingId, request.paymentReference());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Response.success("Payment success applied successfully", booking));
    }

    @PostMapping("/{bookingId}/failure")
    public ResponseEntity<Response<BookingResponse>> failure(
            @PathVariable Long bookingId,
            @Valid @RequestBody PaymentUpdateRequest request) {

        BookingResponse booking =
                paymentResultService.markFailure(bookingId, request.paymentReference());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Response.success("Payment failure applied successfully", booking));
    }
}

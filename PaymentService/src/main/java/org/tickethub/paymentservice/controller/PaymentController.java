package org.tickethub.paymentservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.tickethub.paymentservice.dto.BookingResponse;
import org.tickethub.paymentservice.dto.PaymentRequest;
import org.tickethub.paymentservice.dto.PaymentResponse;
import org.tickethub.paymentservice.dto.PaymentVerificationRequest;
import org.tickethub.paymentservice.dto.ResponseDto;
import org.tickethub.paymentservice.service.PaymentService;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final org.tickethub.paymentservice.client.BookingServiceGateway bookingServiceGateway;

    @PostMapping
    public ResponseEntity<ResponseDto> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String authUserId,
            @RequestHeader(value = "X-Authenticated-User", required = false) String authUser,
            @RequestHeader(value = "X-Authenticated-Role", required = false) String authRole) {

        if (shouldEnforceOwnership(authUserId, authUser, authRole)) {
            var booking = bookingServiceGateway.getBooking(request.getBookingId());
            if (booking != null && !matchesUser(booking, authUserId, authUser)) {
                throw new org.tickethub.paymentservice.exception.ForbiddenException("Cannot initiate payment for another user's booking");
            }
        }

        PaymentResponse paymentResponse = paymentService.createPayment(request);

        ResponseDto response = ResponseDto.builder().message("Payment initiated successfully").data(paymentResponse).error(false).build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ResponseDto> getPayment(
            @PathVariable Long paymentId,
            @RequestHeader(value = "X-User-Id", required = false) String authUserId,
            @RequestHeader(value = "X-Authenticated-User", required = false) String authUser,
            @RequestHeader(value = "X-Authenticated-Role", required = false) String authRole) {

        PaymentResponse paymentResponse = paymentService.getPayment(paymentId);

        if (shouldEnforceOwnership(authUserId, authUser, authRole)) {
            var booking = bookingServiceGateway.getBooking(paymentResponse.getBookingId());
            if (booking != null && !matchesUser(booking, authUserId, authUser)) {
                throw new org.tickethub.paymentservice.exception.ForbiddenException("You are not authorized to view this payment");
            }
        }

        ResponseDto response = ResponseDto.builder().message("Payment fetched successfully").data(paymentResponse).error(false).build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ResponseDto> getPaymentByBookingId(
            @PathVariable Long bookingId,
            @RequestHeader(value = "X-User-Id", required = false) String authUserId,
            @RequestHeader(value = "X-Authenticated-User", required = false) String authUser,
            @RequestHeader(value = "X-Authenticated-Role", required = false) String authRole) {

        if (shouldEnforceOwnership(authUserId, authUser, authRole)) {
            var booking = bookingServiceGateway.getBooking(bookingId);
            if (booking != null && !matchesUser(booking, authUserId, authUser)) {
                throw new org.tickethub.paymentservice.exception.ForbiddenException("You are not authorized to view payment for this booking");
            }
        }

        PaymentResponse paymentResponse = paymentService.getPaymentByBookingId(bookingId);

        ResponseDto response = ResponseDto.builder().message("Payment fetched successfully").data(paymentResponse).error(false).build();

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ResponseDto> getAllPayments(
            @RequestHeader(value = "X-Authenticated-User", required = false) String authUser,
            @RequestHeader(value = "X-Authenticated-Role", required = false) String authRole) {

        if ("CUSTOMER".equalsIgnoreCase(authRole)) {
            throw new org.tickethub.paymentservice.exception.ForbiddenException("Customers are not authorized to view all payment records");
        }

        List<PaymentResponse> payments = paymentService.getAllPayments();

        ResponseDto response = ResponseDto.builder().message("Payments fetched successfully").data(payments).error(false).build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<ResponseDto> checkPaymentStatus(
            @PathVariable Long paymentId,
            @RequestHeader(value = "X-User-Id", required = false) String authUserId,
            @RequestHeader(value = "X-Authenticated-User", required = false) String authUser,
            @RequestHeader(value = "X-Authenticated-Role", required = false) String authRole) {

        PaymentResponse paymentResponse = paymentService.checkPaymentStatus(paymentId);

        if (shouldEnforceOwnership(authUserId, authUser, authRole)) {
            var booking = bookingServiceGateway.getBooking(paymentResponse.getBookingId());
            if (booking != null && !matchesUser(booking, authUserId, authUser)) {
                throw new org.tickethub.paymentservice.exception.ForbiddenException("You are not authorized to view this payment status");
            }
        }

        ResponseDto response = ResponseDto.builder().message("Payment status fetched successfully").data(paymentResponse).error(false).build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<ResponseDto> confirmPayment(
            @PathVariable Long paymentId,
            @RequestParam(value = "razorpayPaymentId", required = false) String razorpayPaymentId,
            @RequestBody(required = false) PaymentVerificationRequest request) {

        PaymentVerificationRequest effectiveRequest = request;
        if (razorpayPaymentId != null && !razorpayPaymentId.isBlank()) {
            if (effectiveRequest == null) {
                effectiveRequest = PaymentVerificationRequest.builder()
                        .paymentId(paymentId)
                        .razorpayPaymentId(razorpayPaymentId.trim())
                        .build();
            } else if (effectiveRequest.getRazorpayPaymentId() == null || effectiveRequest.getRazorpayPaymentId().isBlank()) {
                effectiveRequest.setRazorpayPaymentId(razorpayPaymentId.trim());
            }
        }

        PaymentResponse paymentResponse = paymentService.confirmPayment(paymentId, effectiveRequest);

        ResponseDto response = ResponseDto.builder().message("Payment confirmed successfully").data(paymentResponse).error(false).build();

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<ResponseDto> confirmPayment(
            Long paymentId,
            PaymentVerificationRequest request) {
        return confirmPayment(paymentId, null, request);
    }

    @PostMapping("/verify")
    public ResponseEntity<ResponseDto> verifyPayment(@RequestBody PaymentVerificationRequest request) {
        if (request == null || (request.getPaymentId() == null
                && (request.getRazorpayOrderId() == null || request.getRazorpayOrderId().isBlank()))) {
            throw new IllegalArgumentException("paymentId or razorpayOrderId is required for payment verification");
        }

        PaymentResponse paymentResponse = paymentService.verifyPayment(request);

        ResponseDto response = ResponseDto.builder().message("Payment verified and confirmed successfully").data(paymentResponse).error(false).build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ResponseDto> cancelPayment(@PathVariable Long paymentId) {
        PaymentResponse paymentResponse = paymentService.cancelPayment(paymentId);

        ResponseDto response = ResponseDto.builder().message("Payment cancelled successfully").data(paymentResponse).error(false).build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String headerEventId) {

        paymentService.processWebhook(payload, signature, headerEventId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{paymentId}/resolve-unknown")
    public ResponseEntity<ResponseDto> resolveUnknown(@PathVariable Long paymentId) {
        PaymentResponse paymentResponse = paymentService.resolveUnknownPayment(paymentId);
        ResponseDto response = ResponseDto.builder()
                .message("Payment resolved successfully")
                .data(paymentResponse)
                .error(false)
                .build();
        return ResponseEntity.ok(response);
    }

    private boolean shouldEnforceOwnership(String authUserId, String authUser, String authRole) {
        if ("ADMIN".equalsIgnoreCase(authRole)) {
            return false;
        }
        return (authUserId != null && !authUserId.isBlank())
                || ("CUSTOMER".equalsIgnoreCase(authRole) && authUser != null && !authUser.isBlank());
    }

    private boolean matchesUser(BookingResponse booking, String authUserId, String authUser) {
        if (booking == null || booking.getUserId() == null) {
            return true;
        }
        Long entityUserId = booking.getUserId();
        if (authUserId != null && !authUserId.isBlank()) {
            return String.valueOf(entityUserId).equals(authUserId.trim());
        }
        if (authUser == null || authUser.isBlank()) {
            return true;
        }
        if (authUser.trim().equals(String.valueOf(entityUserId))) {
            return true;
        }
        if (booking.getCustomerEmail() != null && booking.getCustomerEmail().equalsIgnoreCase(authUser.trim())) {
            return true;
        }
        try {
            return entityUserId.equals(Long.parseLong(authUser.trim()));
        } catch (NumberFormatException e) {
            return "customer".equalsIgnoreCase(authUser) || "admin".equalsIgnoreCase(authUser);
        }
    }
}
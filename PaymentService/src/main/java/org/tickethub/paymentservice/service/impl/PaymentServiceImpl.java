package org.tickethub.paymentservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tickethub.paymentservice.client.BookingServiceGateway;
import org.tickethub.paymentservice.dto.BookingResponse;
import org.tickethub.paymentservice.dto.PaymentRequest;
import org.tickethub.paymentservice.dto.PaymentResponse;
import org.tickethub.paymentservice.dto.PaymentVerificationRequest;
import org.tickethub.paymentservice.entity.Payment;
import org.tickethub.paymentservice.entity.PaymentWebhookEvent;
import org.tickethub.paymentservice.entity.enums.PaymentStatus;
import org.tickethub.paymentservice.event.PaymentSuccessEvent;
import org.tickethub.paymentservice.exception.DuplicatePaymentException;
import org.tickethub.paymentservice.exception.PaymentNotFoundException;
import org.tickethub.paymentservice.kafka.PaymentEventProducer;
import org.tickethub.paymentservice.provider.PaymentProvider;
import org.tickethub.paymentservice.repository.PaymentRepository;
import org.tickethub.paymentservice.repository.PaymentWebhookEventRepository;
import org.tickethub.paymentservice.service.PaymentService;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentEventProducer paymentEventProducer;
    private final BookingServiceGateway bookingServiceGateway;
    private final ObjectMapper objectMapper;

    @Value("${razorpay.key-id:rzp_test_simulated_key}")
    private String razorpayKeyId = "rzp_test_simulated_key";

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        /*
         * 1. Idempotency check
         */
        var existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingPayment.isPresent()) {
            return mapToResponse(existingPayment.get());
        }

        /*
         * 1b. Check if a payment already exists for this bookingId
         */
        var existingByBooking = paymentRepository.findByBookingId(request.getBookingId());
        if (existingByBooking.isPresent()) {
            Payment existing = existingByBooking.get();
            if (existing.getStatus() == PaymentStatus.SUCCESS) {
                throw new DuplicatePaymentException("Payment already completed for booking: " + request.getBookingId());
            }
            if (existing.getStatus() == PaymentStatus.PENDING && existing.getRazorpayOrderId() != null) {
                return mapToResponse(existing);
            }
        }

        /*
         * 2. Load the booking from its owning service. Never trust the client
         *    to set the amount that will be charged.
         */
        BookingResponse booking = bookingServiceGateway.getBooking(request.getBookingId());
        if (booking == null || booking.getTotalAmount() == null) {
            throw new IllegalArgumentException("Booking amount is unavailable");
        }
        if (!"PENDING_PAYMENT".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Only bookings awaiting payment can be paid");
        }
        if (request.getAmount() != null && request.getAmount().compareTo(booking.getTotalAmount()) != 0) {
            throw new IllegalArgumentException("Supplied amount does not match the booking total");
        }
        String trustedCurrency = booking.getCurrency() == null || booking.getCurrency().isBlank()
                ? "INR" : booking.getCurrency();
        if (request.getCurrency() != null && !request.getCurrency().isBlank()
                && !request.getCurrency().equalsIgnoreCase(trustedCurrency)) {
            throw new IllegalArgumentException("Supplied currency does not match the booking currency");
        }

        /*
         * 3. Persist or reuse a payment record with the server-calculated booking total.
         */
        Payment payment;
        if (existingByBooking.isPresent()) {
            payment = existingByBooking.get();
            payment.setCurrency(trustedCurrency);
            payment.setPaymentMethod(request.getPaymentMethod());
            payment.setStatus(PaymentStatus.INITIATED);
            payment.setIdempotencyKey(request.getIdempotencyKey());
            payment.setAmount(booking.getTotalAmount());
            payment.setRazorpayPaymentId(null);
            payment.setRazorpaySignature(null);
        } else {
            payment = Payment.builder()
                    .bookingId(request.getBookingId())
                    .currency(trustedCurrency)
                    .paymentMethod(request.getPaymentMethod())
                    .status(PaymentStatus.INITIATED)
                    .idempotencyKey(request.getIdempotencyKey())
                    .amount(booking.getTotalAmount())
                    .build();
        }
        Payment savedPayment = paymentRepository.save(payment);

        /*
         * 4. Create provider order using the trusted amount.
         */
        String razorpayOrderId = paymentProvider.createOrder(savedPayment.getBookingId(), savedPayment.getAmount(), savedPayment.getCurrency());

        /*
         * 5. Update payment with Razorpay order ID
         */
        savedPayment.setRazorpayOrderId(razorpayOrderId);
        savedPayment.setStatus(PaymentStatus.PENDING);

        Payment updatedPayment = paymentRepository.save(savedPayment);

        /*
         * 6. Return immediately. Payment is NOT successful yet.
         */
        return mapToResponse(updatedPayment);
    }

    @Override
    @Transactional
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        return mapToResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse getPaymentByBookingId(Long bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for booking: " + bookingId));
        return mapToResponse(payment);
    }

    @Override
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Override
    public PaymentResponse checkPaymentStatus(Long paymentId) {
        return getPayment(paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Successful payment cannot be cancelled. Refund required.");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        Payment cancelled = paymentRepository.save(payment);
        bookingServiceGateway.markPaymentFailure(cancelled.getBookingId(), "payment-cancelled");
        return mapToResponse(cancelled);
    }

    @Override
    @Transactional
    public PaymentResponse confirmPayment(Long paymentId, PaymentVerificationRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return mapToResponse(payment);
        }

        String orderId = (request != null && request.getRazorpayOrderId() != null && !request.getRazorpayOrderId().isBlank())
                ? request.getRazorpayOrderId()
                : payment.getRazorpayOrderId();
        if (payment.getRazorpayOrderId() != null && orderId != null && !payment.getRazorpayOrderId().equals(orderId)) {
            throw new IllegalArgumentException("Razorpay order ID does not match payment record");
        }

        String razorpayPaymentId = (request != null && request.getRazorpayPaymentId() != null && !request.getRazorpayPaymentId().isBlank())
                ? request.getRazorpayPaymentId()
                : "pay_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String signature = request != null ? request.getRazorpaySignature() : null;

        if (signature != null && !signature.isBlank()) {
            if (!paymentProvider.verifyPayment(orderId, razorpayPaymentId, signature)) {
                throw new SecurityException("Invalid Razorpay payment signature");
            }
        }

        BookingResponse currentBooking = bookingServiceGateway.getBooking(payment.getBookingId());
        String bookingStatus = currentBooking != null && currentBooking.getStatus() != null
                ? currentBooking.getStatus().toUpperCase()
                : "";
        if (!"PENDING_PAYMENT".equals(bookingStatus) && !"CONFIRMED".equals(bookingStatus)) {
            paymentProvider.refund(razorpayPaymentId, payment.getAmount());
            payment.setRazorpayPaymentId(razorpayPaymentId);
            payment.setRazorpaySignature(signature);
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.saveAndFlush(payment);
            throw new IllegalStateException("Booking is no longer awaiting payment; payment has been refunded");
        }

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(signature);
        payment.setStatus(PaymentStatus.SUCCESS);
        Payment updated = paymentRepository.saveAndFlush(payment);

        if (!"CONFIRMED".equals(bookingStatus)) {
            bookingServiceGateway.markPaymentSuccess(updated.getBookingId(), razorpayPaymentId);
        }

        publishPaymentSuccessEvent(updated, currentBooking, razorpayPaymentId, updated.getRazorpayOrderId());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void processWebhook(String payload, String signature) {
        processWebhook(payload, signature, null);
    }

    @Override
    @Transactional
    public void processWebhook(String payload, String signature, String headerEventId) {
        try {
            if (!paymentProvider.verifyWebhookSignature(payload, signature)) {
                throw new SecurityException("Invalid Razorpay webhook signature");
            }

            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("event").asText();
            boolean captured = "payment.captured".equals(eventType);
            boolean failed = "payment.failed".equals(eventType);
            if (!captured && !failed) {
                return;
            }

            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            String razorpayPaymentId = paymentEntity.path("id").asText();
            String razorpayOrderId = paymentEntity.path("order_id").asText();
            if (razorpayOrderId.isBlank()) {
                throw new IllegalArgumentException("Webhook is missing the Razorpay order ID");
            }

            String eventId = (headerEventId != null && !headerEventId.isBlank())
                    ? headerEventId.trim()
                    : root.path("id").asText();
            if (eventId == null || eventId.isBlank()) {
                if (!razorpayPaymentId.isBlank()) {
                    eventId = "evt_" + razorpayPaymentId + "_" + eventType;
                } else {
                    eventId = "evt_" + razorpayOrderId + "_" + eventType;
                }
            }

            if (!eventId.isBlank() && paymentWebhookEventRepository.existsByEventId(eventId)) {
                return;
            }

            Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found for Razorpay order: " + razorpayOrderId));

            PaymentWebhookEvent webhookEvent = null;
            if (!eventId.isBlank()) {
                webhookEvent = PaymentWebhookEvent.builder()
                        .eventId(eventId).eventType(eventType).paymentId(payment.getId())
                        .razorpayPaymentId(razorpayPaymentId).razorpayOrderId(razorpayOrderId)
                        .payload(payload).processed(false).receivedAt(LocalDateTime.now()).build();
                paymentWebhookEventRepository.saveAndFlush(webhookEvent);
            }

            if (captured && payment.getStatus() != PaymentStatus.SUCCESS) {
                BookingResponse currentBooking = bookingServiceGateway.getBooking(payment.getBookingId());
                String bookingStatus = currentBooking.getStatus() == null ? "" : currentBooking.getStatus().toUpperCase();
                if (!"PENDING_PAYMENT".equals(bookingStatus) && !"CONFIRMED".equals(bookingStatus)) {
                    // A captured payment for an expired/cancelled/failed reservation must not create a ticket.
                    // Refund it and record the late provider result instead.
                    paymentProvider.refund(razorpayPaymentId, payment.getAmount());
                    payment.setRazorpayPaymentId(razorpayPaymentId);
                    payment.setRazorpaySignature(signature);
                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.saveAndFlush(payment);
                    if (webhookEvent != null) {
                        webhookEvent.setProcessed(true);
                        webhookEvent.setProcessedAt(LocalDateTime.now());
                        paymentWebhookEventRepository.save(webhookEvent);
                    }
                    return;
                }

                JsonNode amountNode = paymentEntity.path("amount");
                if (amountNode != null && !amountNode.isMissingNode() && !amountNode.isNull() && amountNode.isNumber()) {
                    long webhookAmountPaise = amountNode.asLong();
                    long expectedAmountPaise = payment.getAmount()
                            .setScale(2, RoundingMode.HALF_UP)
                            .movePointRight(2)
                            .longValueExact();
                    if (webhookAmountPaise != expectedAmountPaise) {
                        throw new IllegalArgumentException("Webhook amount (" + webhookAmountPaise
                                + " paise) does not match payment amount (" + expectedAmountPaise + " paise)");
                    }
                }

                payment.setRazorpayPaymentId(razorpayPaymentId);
                payment.setRazorpaySignature(signature);
                payment.setStatus(PaymentStatus.SUCCESS);
                Payment updated = paymentRepository.saveAndFlush(payment);

                // Booking confirmation must be acknowledged before payment-success is announced.
                if (!"CONFIRMED".equals(bookingStatus)) {
                    bookingServiceGateway.markPaymentSuccess(updated.getBookingId(), razorpayPaymentId);
                }
                publishPaymentSuccessEvent(updated, currentBooking, razorpayPaymentId, razorpayOrderId);
            } else if (failed && payment.getStatus() != PaymentStatus.SUCCESS
                    && payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.CANCELLED) {
                payment.setRazorpayPaymentId(razorpayPaymentId);
                payment.setRazorpaySignature(signature);
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.saveAndFlush(payment);
                // Failed payments must release the booking's temporary seat locks.
                bookingServiceGateway.markPaymentFailure(payment.getBookingId(), razorpayPaymentId);
            }

            if (webhookEvent != null) {
                webhookEvent.setProcessed(true);
                webhookEvent.setProcessedAt(LocalDateTime.now());
                paymentWebhookEventRepository.save(webhookEvent);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process payment webhook", e);
        }
    }

    private void publishPaymentSuccessEvent(Payment updated, BookingResponse booking,
                                            String razorpayPaymentId, String razorpayOrderId) {
        PaymentSuccessEvent eventData = PaymentSuccessEvent.builder()
                .paymentId(updated.getId())
                .bookingId(updated.getBookingId())
                .userId(booking != null ? booking.getUserId() : null)
                .recipientEmail(booking != null ? booking.getCustomerEmail() : null)
                .recipientName(booking != null ? booking.getCustomerName() : null)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpayOrderId(razorpayOrderId)
                .amount(updated.getAmount())
                .currency(updated.getCurrency())
                .paymentMethod(updated.getPaymentMethod() != null ? updated.getPaymentMethod().name() : null)
                .paymentStatus(updated.getStatus() != null ? updated.getStatus().name() : "SUCCESS")
                .paidAt(updated.getPaidAt())
                .build();
        paymentEventProducer.publishPaymentSuccess(eventData);
    }

    @Override
    @Transactional
    public PaymentResponse verifyPayment(PaymentVerificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Verification request body is required");
        }
        Long paymentId = request.getPaymentId();
        if (paymentId == null && request.getRazorpayOrderId() != null && !request.getRazorpayOrderId().isBlank()) {
            Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId().trim())
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found for Razorpay order: " + request.getRazorpayOrderId()));
            paymentId = payment.getId();
        }
        if (paymentId == null) {
            throw new IllegalArgumentException("Either paymentId or razorpayOrderId is required for payment verification");
        }
        return confirmPayment(paymentId, request);
    }

    private PaymentResponse mapToResponse(Payment payment) {
        String effectiveKeyId = (razorpayKeyId != null && !razorpayKeyId.isBlank())
                ? razorpayKeyId
                : "rzp_test_simulated_key";
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus() != null ? payment.getStatus().name() : null)
                .paymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null)
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .razorpayKeyId(effectiveKeyId)
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
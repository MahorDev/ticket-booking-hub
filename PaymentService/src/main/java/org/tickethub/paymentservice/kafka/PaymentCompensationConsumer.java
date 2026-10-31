package org.tickethub.paymentservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.paymentservice.entity.Payment;
import org.tickethub.paymentservice.entity.enums.PaymentStatus;
import org.tickethub.paymentservice.event.BookingCancelledEvent;
import org.tickethub.paymentservice.event.BookingRejectedEvent;
import org.tickethub.paymentservice.event.PaymentRefundedEvent;
import org.tickethub.paymentservice.provider.PaymentProvider;
import org.tickethub.paymentservice.repository.PaymentRepository;

import java.time.LocalDateTime;

/**
 * Scenario 5: Saga Compensations & Refunds.
 * Listens to BookingRejected (expired/failed holds) and BookingCancelled (user cancellation).
 * Executes an automated, idempotent refund through the payment provider and announces PaymentRefunded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "booking-rejected", groupId = "payment-compensation-group", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleBookingRejected(String message) {
        try {
            BookingRejectedEvent event = objectMapper.readValue(message, BookingRejectedEvent.class);
            log.info("Received BookingRejectedEvent for bookingId={}. Reason={}", event.getBookingId(), event.getReason());
            processCompensationRefund(event.getBookingId(), "Booking Rejected: " + event.getReason());
        } catch (Exception ex) {
            log.error("Failed to process BookingRejectedEvent: {}", ex.getMessage(), ex);
        }
    }

    @KafkaListener(topics = "booking-cancelled", groupId = "payment-compensation-group", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleBookingCancelled(String message) {
        try {
            BookingCancelledEvent event = objectMapper.readValue(message, BookingCancelledEvent.class);
            log.info("Received BookingCancelledEvent for bookingId={}. Reason={}", event.getBookingId(), event.getReason());
            processCompensationRefund(event.getBookingId(), "Customer Cancelled: " + event.getReason());
        } catch (Exception ex) {
            log.error("Failed to process BookingCancelledEvent: {}", ex.getMessage(), ex);
        }
    }

    private void processCompensationRefund(Long bookingId, String reason) {
        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        if (payment == null) {
            log.warn("Compensation skipped: No payment record found for bookingId={}", bookingId);
            return;
        }

        // Scenario 5 Task 3: Idempotent refund check - never refund twice
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment for bookingId={} is already REFUNDED. Skipping duplicate refund.", bookingId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS && payment.getRazorpayPaymentId() != null) {
            log.info("Executing automated compensation refund for bookingId={}, amount={}", bookingId, payment.getAmount());
            try {
                paymentProvider.refund(payment.getRazorpayPaymentId(), payment.getAmount());
            } catch (Exception e) {
                log.warn("Payment provider refund returned note for {}: {}", payment.getRazorpayPaymentId(), e.getMessage());
            }

            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.saveAndFlush(payment);

            // Publish PaymentRefundedEvent for Booking service to finalize state
            PaymentRefundedEvent refundedEvent = PaymentRefundedEvent.builder()
                    .bookingId(bookingId)
                    .paymentId(payment.getId())
                    .refundId("rfnd_" + payment.getRazorpayPaymentId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .aggregateVersion(3L)
                    .eventTimestamp(LocalDateTime.now())
                    .build();

            paymentEventProducer.publishPaymentRefunded(refundedEvent);
            log.info("Compensation refund completed and PaymentRefunded published for bookingId={}", bookingId);
        } else {
            payment.setStatus(PaymentStatus.CANCELLED);
            paymentRepository.saveAndFlush(payment);
            log.info("Payment for bookingId={} marked CANCELLED (was not captured).", bookingId);
        }
    }
}

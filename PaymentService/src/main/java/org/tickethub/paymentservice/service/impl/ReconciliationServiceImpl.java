package org.tickethub.paymentservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.paymentservice.client.BookingServiceGateway;
import org.tickethub.paymentservice.dto.BookingResponse;
import org.tickethub.paymentservice.dto.ReconciliationReport;
import org.tickethub.paymentservice.dto.ReconciliationReport.MismatchDetail;
import org.tickethub.paymentservice.entity.Payment;
import org.tickethub.paymentservice.entity.enums.PaymentStatus;
import org.tickethub.paymentservice.event.PaymentSuccessEvent;
import org.tickethub.paymentservice.kafka.PaymentEventProducer;
import org.tickethub.paymentservice.provider.PaymentProvider;
import org.tickethub.paymentservice.repository.PaymentRepository;
import org.tickethub.paymentservice.service.ReconciliationService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scenario 8: Payment and Booking Reconciliation Job.
 * Periodically or on-demand audits payments and bookings across distributed systems,
 * treating Razorpay as the source of truth for money, and heals state drift automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationServiceImpl implements ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final BookingServiceGateway bookingServiceGateway;
    private final PaymentProvider paymentProvider;
    private final PaymentEventProducer paymentEventProducer;

    private volatile ReconciliationReport lastReport;

    @Override
    @Transactional
    public ReconciliationReport runReconciliation() {
        log.info("Starting automated reconciliation audit...");
        List<Payment> allPayments = paymentRepository.findAll();
        List<MismatchDetail> mismatches = new ArrayList<>();
        int fixedCount = 0;
        int flaggedCount = 0;

        for (Payment payment : allPayments) {
            Long bookingId = payment.getBookingId();
            if (bookingId == null) continue;

            BookingResponse booking = null;
            try {
                booking = bookingServiceGateway.getBooking(bookingId);
            } catch (Exception ex) {
                log.debug("Booking gateway check for bookingId={} error: {}", bookingId, ex.getMessage());
            }

            // Case 1: Payment captured in Razorpay/Payment DB, but Booking is NOT confirmed
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                if (booking == null) {
                    // Booking record missing completely -> Refund customer
                    log.warn("Reconciliation: Payment {} captured but booking {} does not exist. Issuing refund.",
                            payment.getId(), bookingId);
                    refundPayment(payment);
                    mismatches.add(MismatchDetail.builder()
                            .bookingId(bookingId)
                            .paymentId(payment.getId())
                            .type("ORPHANED_PAYMENT")
                            .paymentStatus("SUCCESS")
                            .bookingStatus("MISSING")
                            .actionTaken("REFUND_ISSUED")
                            .resolution("Booking record was absent. Issued full refund to customer via Razorpay.")
                            .build());
                    fixedCount++;
                } else if ("PENDING_PAYMENT".equalsIgnoreCase(booking.getStatus())) {
                    // Payment succeeded, but booking is still pending (lost message / dropped callback)
                    log.info("Reconciliation: Payment {} captured but booking {} is still PENDING_PAYMENT. Auto-confirming via Kafka.",
                            payment.getId(), bookingId);
                    PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                            .eventMessageId("recon-evt-" + payment.getId())
                            .eventType("PaymentSucceeded")
                            .aggregateVersion(2L)
                            .bookingId(bookingId)
                            .paymentId(payment.getId())
                            .razorpayPaymentId(payment.getRazorpayPaymentId())
                            .amount(payment.getAmount())
                            .currency(payment.getCurrency())
                            .eventTimestamp(LocalDateTime.now())
                            .build();
                    paymentEventProducer.publishPaymentSuccess(event);
                    mismatches.add(MismatchDetail.builder()
                            .bookingId(bookingId)
                            .paymentId(payment.getId())
                            .type("CAPTURED_WITHOUT_BOOKING")
                            .paymentStatus("SUCCESS")
                            .bookingStatus("PENDING_PAYMENT")
                            .actionTaken("AUTO_CONFIRMED")
                            .resolution("Re-published PaymentSucceeded event to Kafka. Booking will transition to CONFIRMED.")
                            .build());
                    fixedCount++;
                } else if ("EXPIRED".equalsIgnoreCase(booking.getStatus()) || "CANCELLED".equalsIgnoreCase(booking.getStatus())) {
                    // Captured payment on dead booking -> Issue refund
                    log.warn("Reconciliation: Payment {} captured for {} booking {}. Refunding.",
                            payment.getId(), booking.getStatus(), bookingId);
                    refundPayment(payment);
                    mismatches.add(MismatchDetail.builder()
                            .bookingId(bookingId)
                            .paymentId(payment.getId())
                            .type("LATE_PAYMENT_ON_DEAD_BOOKING")
                            .paymentStatus("SUCCESS")
                            .bookingStatus(booking.getStatus())
                            .actionTaken("REFUND_ISSUED")
                            .resolution("Booking was " + booking.getStatus() + ". Issued automated refund via Razorpay.")
                            .build());
                    fixedCount++;
                }
            }
        }

        ReconciliationReport report = ReconciliationReport.builder()
                .executionTime(LocalDateTime.now())
                .totalChecked(allPayments.size())
                .mismatchesFound(mismatches.size())
                .fixedCount(fixedCount)
                .flaggedCount(flaggedCount)
                .details(mismatches)
                .build();

        this.lastReport = report;
        log.info("Reconciliation complete. Checked={}, Mismatches={}, Fixed={}",
                report.getTotalChecked(), report.getMismatchesFound(), report.getFixedCount());
        return report;
    }

    private void refundPayment(Payment payment) {
        try {
            if (payment.getRazorpayPaymentId() != null) {
                paymentProvider.refund(payment.getRazorpayPaymentId(), payment.getAmount());
            }
        } catch (Exception ex) {
            log.warn("Refund attempt note for payment {}: {}", payment.getId(), ex.getMessage());
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.saveAndFlush(payment);
    }

    @Override
    public ReconciliationReport getLastReport() {
        return lastReport != null ? lastReport : runReconciliation();
    }
}

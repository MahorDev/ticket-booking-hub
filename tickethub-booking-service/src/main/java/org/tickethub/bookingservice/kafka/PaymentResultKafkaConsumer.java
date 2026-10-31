package org.tickethub.bookingservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.bookingservice.entity.Booking;
import org.tickethub.bookingservice.entity.InboxMessage;
import org.tickethub.bookingservice.entity.enums.BookingStatus;
import org.tickethub.bookingservice.event.BookingRejectedEvent;
import org.tickethub.bookingservice.event.PaymentFailedEvent;
import org.tickethub.bookingservice.event.PaymentRefundedEvent;
import org.tickethub.bookingservice.event.PaymentSucceededEvent;
import org.tickethub.bookingservice.repository.BookingRepository;
import org.tickethub.bookingservice.repository.InboxMessageRepository;
import org.tickethub.bookingservice.repository.SeatLockRepository;
import org.tickethub.bookingservice.service.PaymentResultService;

import java.time.LocalDateTime;

/**
 * Scenario 4: Core Saga with choreography.
 * Consumes PaymentSucceeded & PaymentFailed via Inbox idempotency and Outbox publication.
 * Scenario 5: If payment arrives after booking expired, publishes BookingRejected for automated refund.
 * Scenario 6: Enforces partition ordering by aggregateVersion.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentResultKafkaConsumer {

    private final PaymentResultService paymentResultService;
    private final BookingRepository bookingRepository;
    private final SeatLockRepository seatLockRepository;
    private final InboxMessageRepository inboxRepository;
    private final BookingEventProducer bookingEventProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"payment-success", "payment-succeeded"}, groupId = "booking-saga-group")
    @Transactional
    public void consumePaymentSuccess(String payload) {
        try {
            PaymentSucceededEvent event = objectMapper.readValue(payload, PaymentSucceededEvent.class);
            Long bookingId = event.getBookingId();
            String eventId = (event.getEventMessageId() != null && !event.getEventMessageId().isBlank())
                    ? event.getEventMessageId()
                    : "payment-success-" + bookingId + "-" + event.getRazorpayPaymentId();

            // Scenario 2 & 4: Inbox Idempotency check
            if (inboxRepository.existsByEventId(eventId)) {
                log.info("Duplicate PaymentSucceededEvent ignored by Inbox. eventId={} bookingId={}", eventId, bookingId);
                return;
            }

            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
            if (booking == null) {
                log.warn("Booking not found for payment success: bookingId={}", bookingId);
                recordInbox(eventId, "payment-success", bookingId, "PaymentSucceeded", event.getAggregateVersion(), payload);
                return;
            }

            // Scenario 6: Event Ordering - ignore stale out-of-order events
            if (event.getAggregateVersion() != null && booking.getAggregateVersion() != null
                    && event.getAggregateVersion() < booking.getAggregateVersion()) {
                log.warn("Ignoring stale PaymentSucceeded event version {} < booking version {}",
                        event.getAggregateVersion(), booking.getAggregateVersion());
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            boolean isExpired = (booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(now))
                    || booking.getStatus() == BookingStatus.EXPIRED;

            if (isExpired) {
                // Scenario 5: Payment arrived too late! Must reject and trigger compensation refund
                log.warn("Payment arrived after booking expired for bookingId={}. Triggering BookingRejected compensation refund.", bookingId);
                booking.setStatus(BookingStatus.EXPIRED);
                booking.setAggregateVersion(Math.max(booking.getAggregateVersion(), event.getAggregateVersion() + 1));
                bookingRepository.save(booking);
                seatLockRepository.releaseByBookingId(bookingId, now);

                BookingRejectedEvent rejected = BookingRejectedEvent.builder()
                        .bookingId(bookingId)
                        .reason("Booking hold expired before payment capture")
                        .aggregateVersion(booking.getAggregateVersion())
                        .eventTimestamp(now)
                        .build();

                bookingEventProducer.publishBookingRejected(rejected);
            } else if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                String ref = event.getRazorpayPaymentId() != null ? event.getRazorpayPaymentId() : "pay_ref_" + bookingId;
                paymentResultService.markSuccess(bookingId, ref);
                booking.setAggregateVersion(event.getAggregateVersion());
                bookingRepository.save(booking);
                log.info("Saga Choreography: Booking confirmed via PaymentSucceeded event for bookingId={}", bookingId);
            }

            recordInbox(eventId, "payment-success", bookingId, "PaymentSucceeded", event.getAggregateVersion(), payload);

        } catch (Exception ex) {
            log.error("Failed to process PaymentSucceeded event payload: {}", payload, ex);
            throw new RuntimeException("Error processing PaymentSucceeded event", ex);
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "booking-saga-group")
    @Transactional
    public void consumePaymentFailed(String payload) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
            Long bookingId = event.getBookingId();
            String eventId = (event.getEventMessageId() != null && !event.getEventMessageId().isBlank())
                    ? event.getEventMessageId()
                    : "payment-failed-" + bookingId;

            if (inboxRepository.existsByEventId(eventId)) {
                log.info("Duplicate PaymentFailedEvent ignored by Inbox. eventId={} bookingId={}", eventId, bookingId);
                return;
            }

            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
            if (booking != null && booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                paymentResultService.markFailure(bookingId, event.getReason());
                booking.setAggregateVersion(event.getAggregateVersion());
                bookingRepository.save(booking);
                log.info("Saga Choreography: Booking marked PAYMENT_FAILED and seats released for bookingId={}", bookingId);
            }

            recordInbox(eventId, "payment-failed", bookingId, "PaymentFailed", event.getAggregateVersion(), payload);

        } catch (Exception ex) {
            log.error("Failed to process PaymentFailed event: {}", payload, ex);
        }
    }

    @KafkaListener(topics = "payment-refunded", groupId = "booking-saga-group")
    @Transactional
    public void consumePaymentRefunded(String payload) {
        try {
            PaymentRefundedEvent event = objectMapper.readValue(payload, PaymentRefundedEvent.class);
            Long bookingId = event.getBookingId();
            String eventId = (event.getEventMessageId() != null && !event.getEventMessageId().isBlank())
                    ? event.getEventMessageId()
                    : "payment-refunded-" + bookingId;

            if (inboxRepository.existsByEventId(eventId)) {
                return;
            }

            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
            if (booking != null) {
                booking.setStatus(BookingStatus.REFUNDED);
                booking.setAggregateVersion(event.getAggregateVersion());
                bookingRepository.save(booking);
                log.info("Saga Final State: Booking for bookingId={} recorded as REFUNDED", bookingId);
            }

            recordInbox(eventId, "payment-refunded", bookingId, "PaymentRefunded", event.getAggregateVersion(), payload);

        } catch (Exception ex) {
            log.error("Failed to process PaymentRefunded event: {}", payload, ex);
        }
    }

    private void recordInbox(String eventId, String topic, Long bookingId, String eventType, Long version, String payload) {
        try {
            InboxMessage inbox = InboxMessage.builder()
                    .eventId(eventId)
                    .topic(topic)
                    .bookingId(bookingId)
                    .eventType(eventType)
                    .aggregateVersion(version != null ? version : 1L)
                    .payload(payload)
                    .status("PROCESSED")
                    .processedAt(LocalDateTime.now())
                    .build();
            inboxRepository.save(inbox);
        } catch (Exception ex) {
            log.warn("Inbox record save skipped or already exists: eventId={}", eventId);
        }
    }
}

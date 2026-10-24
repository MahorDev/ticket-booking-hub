package org.tickethub.bookingservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.tickethub.bookingservice.event.BookingConfirmedEvent;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventProducer {
    public static final String TOPIC = "booking-confirmed";
    private final KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;
    private final org.tickethub.bookingservice.service.OutboxService outboxService;

    /**
     * Persist event to the durable outbox table inside the active database transaction.
     * Then immediately attempt publication after commit. If Kafka is down, the outbox
     * scheduler will reliably retry until successful.
     */
    public void publish(BookingConfirmedEvent event) {
        org.tickethub.bookingservice.entity.OutboxMessage outboxMsg = null;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            outboxMsg = outboxService.saveEvent("BOOKING", String.valueOf(event.getBookingId()),
                    "BOOKING_CONFIRMED", TOPIC, event);
            final Long outboxId = outboxMsg.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendDirect(event, outboxId);
                }
            });
        } else {
            send(event);
        }
    }

    public void sendDirect(BookingConfirmedEvent event, Long outboxId) {
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(event.getBookingId()), event).get(5, TimeUnit.SECONDS);
            if (outboxId != null) {
                outboxService.markPublished(outboxId);
            }
            log.info("Successfully published booking-confirmed event for bookingId={}", event.getBookingId());
        } catch (Exception ex) {
            log.warn("Kafka currently unavailable. Booking confirmation safely preserved in Outbox for background retry. bookingId={}",
                    event.getBookingId());
            if (outboxId != null) {
                outboxService.markFailed(outboxId, ex.getMessage());
            }
        }
    }

    private void send(BookingConfirmedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(event.getBookingId()), event).get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.error("Publication failed for bookingId={}", event.getBookingId(), ex);
        }
    }
}

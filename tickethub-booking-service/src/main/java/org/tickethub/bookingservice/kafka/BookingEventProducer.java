package org.tickethub.bookingservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.tickethub.bookingservice.entity.OutboxMessage;
import org.tickethub.bookingservice.event.BookingCancelledEvent;
import org.tickethub.bookingservice.event.BookingConfirmedEvent;
import org.tickethub.bookingservice.event.BookingRejectedEvent;
import org.tickethub.bookingservice.service.OutboxService;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class BookingEventProducer {

    public static final String TOPIC_CONFIRMED = "booking-confirmed";
    public static final String TOPIC_REJECTED = "booking-rejected";
    public static final String TOPIC_CANCELLED = "booking-cancelled";

    @SuppressWarnings("rawtypes")
    private final KafkaTemplate kafkaTemplate;
    private final OutboxService outboxService;

    @SuppressWarnings("rawtypes")
    public BookingEventProducer(KafkaTemplate kafkaTemplate, OutboxService outboxService) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxService = outboxService;
    }

    /**
     * Persist event to the durable outbox table inside the active database transaction.
     * Then immediately attempt publication after commit. If Kafka is down, the outbox
     * scheduler will reliably retry until successful.
     */
    public void publish(BookingConfirmedEvent event) {
        publishEvent("BOOKING", String.valueOf(event.getBookingId()), "BOOKING_CONFIRMED", TOPIC_CONFIRMED, event);
    }

    public void publishBookingRejected(BookingRejectedEvent event) {
        publishEvent("BOOKING", String.valueOf(event.getBookingId()), "BOOKING_REJECTED", TOPIC_REJECTED, event);
    }

    public void publishBookingCancelled(BookingCancelledEvent event) {
        publishEvent("BOOKING", String.valueOf(event.getBookingId()), "BOOKING_CANCELLED", TOPIC_CANCELLED, event);
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String topic, Object event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            OutboxMessage outboxMsg = outboxService.saveEvent(aggregateType, aggregateId, eventType, topic, event);
            final Long outboxId = outboxMsg.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendDirect(topic, aggregateId, event, outboxId);
                }
            });
        } else {
            sendDirect(topic, aggregateId, event, null);
        }
    }

    public void sendDirect(BookingConfirmedEvent event, Long outboxId) {
        sendDirect(TOPIC_CONFIRMED, String.valueOf(event.getBookingId()), event, outboxId);
    }

    @SuppressWarnings("unchecked")
    public void sendDirect(String topic, String key, Object event, Long outboxId) {
        try {
            kafkaTemplate.send(topic, key, event).get(5, TimeUnit.SECONDS);
            if (outboxId != null) {
                outboxService.markPublished(outboxId);
            }
            log.info("Successfully published event to topic={} key={}", topic, key);
        } catch (Exception ex) {
            log.warn("Kafka unavailable; event safely preserved in Booking Outbox for retry. topic={} key={} error={}",
                    topic, key, ex.getMessage());
            if (outboxId != null) {
                outboxService.markFailed(outboxId, ex.getMessage());
            }
        }
    }
}

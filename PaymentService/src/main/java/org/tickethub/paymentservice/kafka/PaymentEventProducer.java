package org.tickethub.paymentservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.tickethub.paymentservice.entity.OutboxMessage;
import org.tickethub.paymentservice.event.PaymentFailedEvent;
import org.tickethub.paymentservice.event.PaymentRefundedEvent;
import org.tickethub.paymentservice.event.PaymentSuccessEvent;
import org.tickethub.paymentservice.service.PaymentOutboxService;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    public static final String PAYMENT_SUCCESS_TOPIC = "payment-success";
    public static final String PAYMENT_FAILED_TOPIC = "payment-failed";
    public static final String PAYMENT_REFUNDED_TOPIC = "payment-refunded";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentOutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Publishes PaymentSuccessEvent via Transactional Outbox.
     * Uses bookingId as the partition key for strict event ordering (Scenario 6).
     */
    public void publishPaymentSuccess(PaymentSuccessEvent event) {
        String aggregateId = String.valueOf(event.getBookingId());
        publishThroughOutbox("PAYMENT", aggregateId, "PaymentSucceeded", PAYMENT_SUCCESS_TOPIC, event);
    }

    /**
     * Publishes PaymentFailedEvent via Transactional Outbox.
     */
    public void publishPaymentFailed(PaymentFailedEvent event) {
        String aggregateId = String.valueOf(event.getBookingId());
        publishThroughOutbox("PAYMENT", aggregateId, "PaymentFailed", PAYMENT_FAILED_TOPIC, event);
    }

    /**
     * Publishes PaymentRefundedEvent via Transactional Outbox (Scenario 5 Compensation).
     */
    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        String aggregateId = String.valueOf(event.getBookingId());
        publishThroughOutbox("PAYMENT", aggregateId, "PaymentRefunded", PAYMENT_REFUNDED_TOPIC, event);
    }

    private void publishThroughOutbox(String aggregateType, String aggregateId, String eventType, String topic, Object event) {
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

    private void sendDirect(String topic, String key, Object event, Long outboxId) {
        try {
            kafkaTemplate.send(topic, key, event).get(5, TimeUnit.SECONDS);
            if (outboxId != null) {
                outboxService.markPublished(outboxId);
            }
            log.info("Directly published event to topic={} with key={}", topic, key);
        } catch (Exception ex) {
            log.warn("Kafka broker unavailable; event safely preserved in Payment Outbox for retry. key={} error={}",
                    key, ex.getMessage());
            if (outboxId != null) {
                outboxService.markFailed(outboxId, ex.getMessage());
            }
        }
    }
}

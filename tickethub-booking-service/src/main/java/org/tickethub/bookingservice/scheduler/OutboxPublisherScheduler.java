package org.tickethub.bookingservice.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tickethub.bookingservice.entity.OutboxMessage;
import org.tickethub.bookingservice.event.BookingConfirmedEvent;
import org.tickethub.bookingservice.service.OutboxService;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class OutboxPublisherScheduler {

    private final OutboxService outboxService;
    @SuppressWarnings("rawtypes")
    private final KafkaTemplate kafkaTemplate;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("rawtypes")
    public OutboxPublisherScheduler(OutboxService outboxService, KafkaTemplate kafkaTemplate, ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.publish-interval-ms:5000}")
    @SuppressWarnings("unchecked")
    public void processOutboxMessages() {
        List<OutboxMessage> pendingMessages = outboxService.getPendingMessages();
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox messages to publish", pendingMessages.size());

        for (OutboxMessage msg : pendingMessages) {
            if (msg.getRetryCount() >= 10) {
                continue;
            }

            try {
                // Scenario 6: bookingId (aggregateId) as key
                if ("booking-confirmed".equals(msg.getTopic())) {
                    BookingConfirmedEvent event = objectMapper.readValue(msg.getPayload(), BookingConfirmedEvent.class);
                    kafkaTemplate.send(msg.getTopic(), msg.getAggregateId(), event).get(5, TimeUnit.SECONDS);
                } else {
                    kafkaTemplate.send(msg.getTopic(), msg.getAggregateId(), msg.getPayload()).get(5, TimeUnit.SECONDS);
                }
                outboxService.markPublished(msg.getId());
                log.info("Durable Outbox published message id={} topic={} for aggregateId={}",
                        msg.getId(), msg.getTopic(), msg.getAggregateId());
            } catch (Exception ex) {
                log.warn("Retry failed for outbox message id={}, retryCount={}: {}",
                        msg.getId(), msg.getRetryCount() + 1, ex.getMessage());
                outboxService.markFailed(msg.getId(), ex.getMessage());
            }
        }
    }
}

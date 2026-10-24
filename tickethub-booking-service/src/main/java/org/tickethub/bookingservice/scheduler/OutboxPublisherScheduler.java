package org.tickethub.bookingservice.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherScheduler {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.publish-interval-ms:5000}")
    public void processOutboxMessages() {
        List<OutboxMessage> pendingMessages = outboxService.getPendingMessages();
        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox messages to publish", pendingMessages.size());

        for (OutboxMessage msg : pendingMessages) {
            if (msg.getRetryCount() >= 10) {
                // Max retries reached; leave as failed for manual dead-letter inspection
                continue;
            }

            try {
                if ("booking-confirmed".equals(msg.getTopic())) {
                    BookingConfirmedEvent event = objectMapper.readValue(msg.getPayload(), BookingConfirmedEvent.class);
                    kafkaTemplate.send(msg.getTopic(), msg.getAggregateId(), event).get(5, TimeUnit.SECONDS);
                    outboxService.markPublished(msg.getId());
                    log.info("Durable Outbox published message id={} for aggregateId={}", msg.getId(), msg.getAggregateId());
                }
            } catch (Exception ex) {
                log.warn("Retry failed for outbox message id={}, retryCount={}: {}",
                        msg.getId(), msg.getRetryCount() + 1, ex.getMessage());
                outboxService.markFailed(msg.getId(), ex.getMessage());
            }
        }
    }
}

package org.tickethub.paymentservice.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tickethub.paymentservice.entity.OutboxMessage;
import org.tickethub.paymentservice.service.PaymentOutboxService;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherScheduler {

    private final PaymentOutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.publish-interval-ms:5000}")
    public void processOutboxMessages() {
        List<OutboxMessage> pending = outboxService.getPendingMessages();
        if (pending == null || pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox messages in PaymentService", pending.size());

        for (OutboxMessage msg : pending) {
            if (msg.getRetryCount() >= 10) {
                continue;
            }

            try {
                // Key is aggregateId (bookingId) for Scenario 6 strict partition ordering
                kafkaTemplate.send(msg.getTopic(), msg.getAggregateId(), msg.getPayload()).get(5, TimeUnit.SECONDS);
                outboxService.markPublished(msg.getId());
                log.info("Payment Outbox published message id={} topic={} aggregateId={}",
                        msg.getId(), msg.getTopic(), msg.getAggregateId());
            } catch (Exception ex) {
                log.warn("Retry failed for payment outbox message id={}, retryCount={}: {}",
                        msg.getId(), msg.getRetryCount() + 1, ex.getMessage());
                outboxService.markFailed(msg.getId(), ex.getMessage());
            }
        }
    }
}

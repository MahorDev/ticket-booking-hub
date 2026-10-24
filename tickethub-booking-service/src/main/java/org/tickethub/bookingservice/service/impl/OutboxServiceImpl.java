package org.tickethub.bookingservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.bookingservice.entity.OutboxMessage;
import org.tickethub.bookingservice.repository.OutboxMessageRepository;
import org.tickethub.bookingservice.service.OutboxService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class OutboxServiceImpl implements OutboxService {

    private final OutboxMessageRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxServiceImpl(OutboxMessageRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        ObjectMapper mapper = (objectMapper != null) ? objectMapper.copy() : new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage saveEvent(String aggregateType, String aggregateId, String eventType, String topic, Object payload) {
        try {
            String jsonPayload = (payload instanceof String str) ? str : objectMapper.writeValueAsString(payload);
            OutboxMessage message = OutboxMessage.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(jsonPayload)
                    .status("PENDING")
                    .retryCount(0)
                    .build();
            return outboxRepository.save(message);
        } catch (Exception ex) {
            log.error("Failed to serialize outbox message for aggregateId={}", aggregateId, ex);
            throw new IllegalStateException("Failed to serialize outbox message", ex);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long messageId) {
        outboxRepository.findById(messageId).ifPresent(msg -> {
            msg.setStatus("PUBLISHED");
            msg.setUpdatedAt(LocalDateTime.now());
            outboxRepository.save(msg);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long messageId, String error) {
        outboxRepository.findById(messageId).ifPresent(msg -> {
            int nextRetry = msg.getRetryCount() + 1;
            msg.setRetryCount(nextRetry);
            msg.setStatus(nextRetry >= 10 ? "DEAD_LETTER" : "FAILED");
            msg.setLastError(error != null && error.length() > 990 ? error.substring(0, 990) : error);
            msg.setUpdatedAt(LocalDateTime.now());
            outboxRepository.save(msg);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxMessage> getPendingMessages() {
        List<OutboxMessage> eligible = outboxRepository
                .findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(List.of("PENDING", "FAILED"), 10);
        if (eligible != null && !eligible.isEmpty()) {
            return eligible;
        }
        List<OutboxMessage> fallback = outboxRepository
                .findTop50ByStatusInOrderByCreatedAtAsc(List.of("PENDING", "FAILED"));
        if (fallback == null) {
            return List.of();
        }
        return fallback.stream()
                .filter(m -> m.getRetryCount() < 10)
                .toList();
    }
}

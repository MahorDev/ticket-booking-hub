package org.tickethub.notificationservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.tickethub.notificationservice.entity.DltMessage;
import org.tickethub.notificationservice.repository.DltMessageRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterConsumer {
    private final DltMessageRepository dltMessageRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @KafkaListener(topics = {"booking.confirmed.DLT", "payment.success.DLT"}, groupId = "notification-dlt-inspector-group")
    public void consumeDeadLetterRecord(ConsumerRecord<String, Object> record,
                                        @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                        @Header(name = "kafka_dlt-original-topic", required = false) byte[] originalTopicBytes,
                                        @Header(name = "kafka_dlt-exception-message", required = false) byte[] exceptionBytes) {
        String originalTopic = originalTopicBytes != null ? new String(originalTopicBytes, StandardCharsets.UTF_8) : topic;
        String exceptionMessage = exceptionBytes != null ? new String(exceptionBytes, StandardCharsets.UTF_8) : "Unknown processing error";
        
        String payload;
        if (record.value() instanceof String str) {
            payload = str;
        } else if (record.value() instanceof byte[] bytes) {
            payload = new String(bytes, StandardCharsets.UTF_8);
        } else {
            try {
                payload = objectMapper.writeValueAsString(record.value());
            } catch (Exception e) {
                payload = String.valueOf(record.value());
            }
        }

        log.warn("DLT Record captured: topic={} originalTopic={} key={} exception={}",
                topic, originalTopic, record.key(), exceptionMessage);

        DltMessage dlt = DltMessage.builder()
                .topic(topic != null ? topic : "DLT")
                .originalTopic(originalTopic != null ? originalTopic : "booking-confirmed")
                .messageKey(record.key())
                .payload(payload)
                .exceptionMessage(exceptionMessage != null && exceptionMessage.length() > 1900 ? exceptionMessage.substring(0, 1900) : exceptionMessage)
                .status("FAILED")
                .createdAt(LocalDateTime.now())
                .build();

        dltMessageRepository.save(dlt);
    }
}

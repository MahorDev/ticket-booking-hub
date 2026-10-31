package org.tickethub.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.tickethub.notificationservice.entity.DltMessage;
import org.tickethub.notificationservice.repository.DltMessageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications/dlt")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dead Letter Topic Management", description = "Scenario 1: Inspect & replay dead-lettered messages")
public class DeadLetterController {
    private final DltMessageRepository dltMessageRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "List all dead-lettered messages")
    public ResponseEntity<List<DltMessage>> listDltMessages(@RequestParam(defaultValue = "FAILED") String status) {
        return ResponseEntity.ok(dltMessageRepository.findByStatusOrderByCreatedAtDesc(status));
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "Replay a fixed message from the Dead Letter Topic")
    public ResponseEntity<Map<String, Object>> replayDltMessage(@PathVariable Long id) {
        DltMessage dlt = dltMessageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DLT message not found with ID: " + id));

        String targetTopic = dlt.getOriginalTopic() != null ? dlt.getOriginalTopic() : "booking-confirmed";
        log.info("Replaying DLT message id={} back to targetTopic={}", id, targetTopic);

        Object payloadToSend;
        try {
            payloadToSend = objectMapper.readTree(dlt.getPayload());
        } catch (Exception ex) {
            payloadToSend = dlt.getPayload();
        }

        kafkaTemplate.send(targetTopic, dlt.getMessageKey(), payloadToSend);

        dlt.setStatus("REPROCESSED");
        dlt.setReprocessedAt(LocalDateTime.now());
        dltMessageRepository.save(dlt);

        return ResponseEntity.ok(Map.of(
                "status", "REPLAYED",
                "messageId", id,
                "targetTopic", targetTopic,
                "replayedAt", LocalDateTime.now()
        ));
    }
}

package org.tickethub.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inbox_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inbox_event_id", columnNames = {"event_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "aggregate_id", length = 64)
    private String aggregateId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "PROCESSED";

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}

package org.tickethub.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dlt_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DltMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "original_topic", nullable = false, length = 128)
    private String originalTopic;

    @Column(name = "message_key", length = 128)
    private String messageKey;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "exception_message", length = 2000)
    private String exceptionMessage;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "FAILED";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reprocessed_at")
    private LocalDateTime reprocessedAt;
}

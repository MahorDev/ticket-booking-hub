package org.tickethub.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(name = "uk_notification_event_key", columnNames = "event_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    /*
     * Booking that caused this notification.
     * We store the ID rather than creating a JPA relationship
     * because Booking belongs to another microservice.
     */
    @Column(nullable = false)
    private Long bookingId;

    @Column(name = "event_key", nullable = false, unique = true, length = 180)
    private String eventKey;

    @Column(name = "notification_type", nullable = false, length = 40)
    private String notificationType;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /*
     * EMAIL, SMS, etc.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    /*
     * Destination of the notification.
     * Example: customer@gmail.com
     */
    @Column(nullable = false, length = 255)
    private String recipient;

    /*
     * PENDING -> SENT / FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    /*
     * Reference returned by the external notification provider.
     */
    @Column(length = 100)
    private String providerReference;

    /*
     * Audit timestamps.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime sentAt;
}
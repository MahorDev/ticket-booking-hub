package org.tickethub.notificationservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.notificationservice.entity.InboxMessage;
import org.tickethub.notificationservice.entity.Notification;
import org.tickethub.notificationservice.entity.NotificationChannel;
import org.tickethub.notificationservice.entity.NotificationStatus;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;
import org.tickethub.notificationservice.repository.InboxMessageRepository;
import org.tickethub.notificationservice.repository.NotificationRepository;
import org.tickethub.notificationservice.service.EmailNotificationService;

import java.time.LocalDateTime;

/**
 * Notification persistence and delivery are owned by Notification Service.
 * Implements Scenario 1 (Bad data validation) and Scenario 2 (Idempotent Inbox pattern).
 */
@Service
@Slf4j
public class NotificationDeliveryService {
    private final NotificationRepository repository;
    private final InboxMessageRepository inboxRepository;
    private final EmailNotificationService emailService;

    @Autowired
    public NotificationDeliveryService(NotificationRepository repository,
                                       InboxMessageRepository inboxRepository,
                                       EmailNotificationService emailService) {
        this.repository = repository;
        this.inboxRepository = inboxRepository;
        this.emailService = emailService;
    }

    public NotificationDeliveryService(NotificationRepository repository, EmailNotificationService emailService) {
        this.repository = repository;
        this.inboxRepository = null;
        this.emailService = emailService;
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public void deliverPaymentSuccess(PaymentSuccessEvent event, String recipient) {
        Long bookingId = event.getBookingId();
        String keySuffix = event.getPaymentId() != null
                ? String.valueOf(event.getPaymentId())
                : (event.getRazorpayPaymentId() != null ? event.getRazorpayPaymentId() : String.valueOf(bookingId));
        String eventId = "payment-success:" + keySuffix;

        // Scenario 2: Inbox deduplication
        if (inboxRepository != null && inboxRepository.existsByEventId(eventId)) {
            log.info("Duplicate payment event ignored (Inbox dedup) eventId={}", eventId);
            return;
        }

        Notification notification = prepare(eventId, bookingId, recipient, "PAYMENT_SUCCESS");
        if (notification.getStatus() == NotificationStatus.SENT) {
            log.info("Duplicate payment notification ignored eventKey={}", eventId);
            return;
        }
        try {
            emailService.sendPaymentSuccessEmail(recipient, event);
            notification.setProviderReference(event.getRazorpayPaymentId() != null
                    ? event.getRazorpayPaymentId()
                    : keySuffix);
            markSent(notification);

            // Record in inbox table in the same transaction
            recordInbox(eventId, "PAYMENT_SUCCESS", "payment-success", String.valueOf(bookingId));
        } catch (Exception ex) {
            markFailed(notification, ex);
            throw new IllegalStateException("Payment notification delivery failed", ex);
        }
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public void deliverPaymentSuccess(String eventKey, Long bookingId, String recipient,
                                      String paymentId, String amount, String currency) {
        if (inboxRepository != null && inboxRepository.existsByEventId(eventKey)) {
            log.info("Duplicate notification ignored (Inbox dedup) eventKey={}", eventKey);
            return;
        }
        Notification notification = prepare(eventKey, bookingId, recipient, "PAYMENT_SUCCESS");
        if (notification.getStatus() == NotificationStatus.SENT) {
            log.info("Duplicate notification ignored eventKey={}", eventKey);
            return;
        }
        try {
            emailService.sendPaymentSuccessEmail(recipient, bookingId, paymentId, amount, currency);
            notification.setProviderReference(paymentId);
            markSent(notification);
            recordInbox(eventKey, "PAYMENT_SUCCESS", "payment-success", String.valueOf(bookingId));
        } catch (Exception ex) {
            markFailed(notification, ex);
            throw new IllegalStateException("Payment notification delivery failed", ex);
        }
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public void deliverBookingConfirmation(BookingConfirmedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("BookingConfirmedEvent cannot be null");
        }
        if (event.getBookingId() == null || event.getBookingId() <= 0) {
            throw new IllegalArgumentException("BookingConfirmedEvent must contain a valid bookingId");
        }
        if (event.getRecipientEmail() == null || event.getRecipientEmail().isBlank()) {
            throw new IllegalArgumentException("BookingConfirmedEvent must contain a recipient email address (bad data for DLT)");
        }

        String eventId = event.getEventMessageId();
        if (eventId == null || eventId.isBlank()) {
            eventId = "booking-confirmed:" + event.getBookingId();
        }

        // Scenario 2: Inbox check prevents duplicate processing
        if (inboxRepository != null && inboxRepository.existsByEventId(eventId)) {
            log.info("Duplicate booking confirmed event rejected by Inbox. eventId={} bookingId={}",
                    eventId, event.getBookingId());
            return;
        }

        String recipient = event.getRecipientEmail();
        Notification notification = prepare(eventId, event.getBookingId(), recipient, "BOOKING_CONFIRMED");
        if (notification.getStatus() == NotificationStatus.SENT) {
            log.info("Duplicate booking notification ignored eventKey={}", eventId);
            return;
        }
        try {
            emailService.sendBookingConfirmationEmail(recipient, event);
            if (event.getTicketNumber() != null && !event.getTicketNumber().isBlank()) {
                notification.setProviderReference(event.getTicketNumber());
            }
            markSent(notification);

            // Save inbox row in same transaction as notification
            recordInbox(eventId, "BOOKING_CONFIRMED", "booking-confirmed", String.valueOf(event.getBookingId()));
        } catch (Exception ex) {
            markFailed(notification, ex);
            throw new IllegalStateException("Booking confirmation email delivery failed", ex);
        }
    }

    private void recordInbox(String eventId, String type, String topic, String aggregateId) {
        if (inboxRepository == null) return;
        InboxMessage inbox = InboxMessage.builder()
                .eventId(eventId)
                .eventType(type)
                .topic(topic)
                .aggregateId(aggregateId)
                .status("PROCESSED")
                .processedAt(LocalDateTime.now())
                .build();
        inboxRepository.save(inbox);
    }

    private Notification prepare(String eventKey, Long bookingId, String recipient, String type) {
        if (eventKey == null || eventKey.isBlank()) throw new IllegalArgumentException("Notification event key is required");
        if (bookingId == null || bookingId <= 0) throw new IllegalArgumentException("A valid booking ID is required");
        if (recipient == null || recipient.isBlank()) throw new IllegalArgumentException("Notification recipient is required");

        return repository.findByEventKey(eventKey).orElseGet(() ->
                Notification.builder()
                        .eventKey(eventKey)
                        .bookingId(bookingId)
                        .notificationType(type)
                        .channel(NotificationChannel.EMAIL)
                        .recipient(recipient)
                        .status(NotificationStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .build());
    }

    private void markSent(Notification notification) {
        notification.setStatus(NotificationStatus.SENT);
        notification.setErrorMessage(null);
        notification.setSentAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());
        repository.save(notification);
    }

    private void markFailed(Notification notification, Exception ex) {
        notification.setStatus(NotificationStatus.RETRYING);
        notification.setErrorMessage(truncate(ex.getMessage(), 1000));
        notification.setUpdatedAt(LocalDateTime.now());
        repository.save(notification);
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }
}

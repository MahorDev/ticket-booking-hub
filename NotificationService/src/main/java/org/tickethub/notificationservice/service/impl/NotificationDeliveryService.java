package org.tickethub.notificationservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.notificationservice.entity.Notification;
import org.tickethub.notificationservice.entity.NotificationChannel;
import org.tickethub.notificationservice.entity.NotificationStatus;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;
import org.tickethub.notificationservice.repository.NotificationRepository;
import org.tickethub.notificationservice.service.EmailNotificationService;

import java.time.LocalDateTime;

/**
 * Notification persistence and delivery are owned by Notification Service.
 * Booking confirmation is never rolled back when email delivery fails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryService {
    private final NotificationRepository repository;
    private final EmailNotificationService emailService;

    @Transactional(noRollbackFor = IllegalStateException.class)
    public void deliverPaymentSuccess(PaymentSuccessEvent event, String recipient) {
        Long bookingId = event.getBookingId();
        String keySuffix = event.getPaymentId() != null
                ? String.valueOf(event.getPaymentId())
                : (event.getRazorpayPaymentId() != null ? event.getRazorpayPaymentId() : String.valueOf(bookingId));
        String eventKey = "payment-success:" + keySuffix;
        Notification notification = prepare(eventKey, bookingId, recipient, "PAYMENT_SUCCESS");
        if (notification.getStatus() == NotificationStatus.SENT) {
            log.info("Duplicate payment notification ignored eventKey={}", eventKey);
            return;
        }
        try {
            emailService.sendPaymentSuccessEmail(recipient, event);
            notification.setProviderReference(event.getRazorpayPaymentId() != null
                    ? event.getRazorpayPaymentId()
                    : keySuffix);
            markSent(notification);
        } catch (Exception ex) {
            markFailed(notification, ex);
            throw new IllegalStateException("Payment notification delivery failed", ex);
        }
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public void deliverPaymentSuccess(String eventKey, Long bookingId, String recipient,
                                      String paymentId, String amount, String currency) {
        Notification notification = prepare(eventKey, bookingId, recipient, "PAYMENT_SUCCESS");
        if (notification.getStatus() == NotificationStatus.SENT) {
            log.info("Duplicate notification ignored eventKey={}", eventKey);
            return;
        }
        try {
            emailService.sendPaymentSuccessEmail(recipient, bookingId, paymentId, amount, currency);
            notification.setProviderReference(paymentId);
            markSent(notification);
        } catch (Exception ex) {
            markFailed(notification, ex);
            // Throwing lets Kafka retry this delivery; FAILED records are retryable.
            throw new IllegalStateException("Payment notification delivery failed", ex);
        }
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public void deliverBookingConfirmation(BookingConfirmedEvent event) {
        String eventKey = "booking-confirmed:" + event.getBookingId();
        Long bookingId = event.getBookingId();
        String recipient = event.getRecipientEmail();
        if (recipient == null || recipient.isBlank()) {
            recipient = "customer@tickethub.com";
        }
        Notification notification = prepare(eventKey, bookingId, recipient, "BOOKING_CONFIRMED");
        if (notification.getStatus() == NotificationStatus.SENT) {
            log.info("Duplicate booking notification ignored eventKey={}", eventKey);
            return;
        }
        try {
            emailService.sendBookingConfirmationEmail(recipient, event);
            if (event.getTicketNumber() != null && !event.getTicketNumber().isBlank()) {
                notification.setProviderReference(event.getTicketNumber());
            }
            markSent(notification);
        } catch (Exception ex) {
            markFailed(notification, ex);
            throw new IllegalStateException("Booking confirmation email delivery failed", ex);
        }
    }

    private Notification prepare(String eventKey, Long bookingId, String recipient, String type) {
        if (eventKey == null || eventKey.isBlank()) throw new IllegalArgumentException("Notification event key is required");
        if (bookingId == null || bookingId <= 0) throw new IllegalArgumentException("A valid booking ID is required");
        if (recipient == null || recipient.isBlank()) throw new IllegalArgumentException("Notification recipient is required");

        Notification notification = repository.findByEventKey(eventKey).orElseGet(() ->
                Notification.builder()
                        .eventKey(eventKey)
                        .bookingId(bookingId)
                        .notificationType(type)
                        .channel(NotificationChannel.EMAIL)
                        .recipient(recipient)
                        .status(NotificationStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .build());
        if (notification.getStatus() != NotificationStatus.SENT) {
            notification.setStatus(NotificationStatus.PENDING);
            notification.setErrorMessage(null);
            notification.setUpdatedAt(LocalDateTime.now());
            notification.setRecipient(recipient);
        }
        return repository.saveAndFlush(notification);
    }

    private void markSent(Notification notification) {
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());
        notification.setErrorMessage(null);
        repository.save(notification);
    }

    private void markFailed(Notification notification, Exception ex) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setUpdatedAt(LocalDateTime.now());
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        notification.setErrorMessage(message.substring(0, Math.min(message.length(), 1000)));
        repository.save(notification);
    }
}

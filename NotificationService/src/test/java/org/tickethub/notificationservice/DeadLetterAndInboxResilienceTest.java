package org.tickethub.notificationservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.tickethub.notificationservice.entity.DltMessage;
import org.tickethub.notificationservice.entity.InboxMessage;
import org.tickethub.notificationservice.entity.Notification;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.repository.DltMessageRepository;
import org.tickethub.notificationservice.repository.InboxMessageRepository;
import org.tickethub.notificationservice.repository.NotificationRepository;
import org.tickethub.notificationservice.service.EmailNotificationService;
import org.tickethub.notificationservice.service.impl.EmailNotificationServiceImpl;
import org.tickethub.notificationservice.service.impl.NotificationDeliveryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeadLetterAndInboxResilienceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private InboxMessageRepository inboxRepository;

    @Mock
    private DltMessageRepository dltRepository;

    @Mock
    private JavaMailSender mailSender;

    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        EmailNotificationService emailService = new EmailNotificationServiceImpl(mailSender);
        deliveryService = new NotificationDeliveryService(notificationRepository, inboxRepository, emailService);
    }

    private BookingConfirmedEvent validEvent(String eventMessageId, Long bookingId, String email) {
        return BookingConfirmedEvent.builder()
                .eventMessageId(eventMessageId)
                .bookingId(bookingId)
                .recipientEmail(email)
                .recipientName("Alice Test")
                .eventTitle("Inception")
                .showDate(LocalDate.now())
                .startTime(LocalTime.of(19, 0))
                .venueName("Grand Cinema")
                .screenName("Audi 1")
                .totalAmount(new BigDecimal("500.00"))
                .ticketNumber("TKT-VALID-100")
                .build();
    }

    @Test
    @DisplayName("Scenario 1: Bad data with missing email throws IllegalArgumentException (routes to DLT)")
    void testBadDataThrowsNonRetryableException() {
        BookingConfirmedEvent badEvent = validEvent("evt-bad-1", 101L, ""); // empty email

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                deliveryService.deliverBookingConfirmation(badEvent));

        assertTrue(ex.getMessage().contains("must contain a recipient email address"));
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Scenario 2: The same event arrives twice — Inbox rejects duplicate with 1 email sent")
    void testSameEventArrivesTwiceInboxDeduplication() {
        String eventId = "evt-duplicate-1001";
        BookingConfirmedEvent event = validEvent(eventId, 202L, "alice@tickethub.com");

        when(inboxRepository.existsByEventId(eventId)).thenReturn(false).thenReturn(true);
        when(notificationRepository.findByEventKey(any())).thenReturn(Optional.empty());

        // First delivery attempt
        deliveryService.deliverBookingConfirmation(event);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        verify(inboxRepository, times(1)).save(any(InboxMessage.class));

        // Second delivery attempt (replay)
        deliveryService.deliverBookingConfirmation(event);

        // Verification: Exactly ONE email was sent, second invocation was rejected by Inbox
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}

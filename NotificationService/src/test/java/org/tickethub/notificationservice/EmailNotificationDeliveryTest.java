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
import org.tickethub.notificationservice.entity.Notification;
import org.tickethub.notificationservice.entity.NotificationStatus;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;
import org.tickethub.notificationservice.event.SeatInfo;
import org.tickethub.notificationservice.repository.NotificationRepository;
import org.tickethub.notificationservice.service.EmailNotificationService;
import org.tickethub.notificationservice.service.impl.EmailNotificationServiceImpl;
import org.tickethub.notificationservice.service.impl.NotificationDeliveryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailNotificationDeliveryTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationRepository repository;

    private EmailNotificationService emailService;
    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        emailService = new EmailNotificationServiceImpl(mailSender);
        deliveryService = new NotificationDeliveryService(repository, emailService);
    }

    private BookingConfirmedEvent sampleEvent() {
        return BookingConfirmedEvent.builder()
                .bookingId(2001L)
                .bookingReference("BK-20261012-777")
                .eventTitle("Interstellar IMAX")
                .showDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(20, 0))
                .endTime(LocalTime.of(22, 49))
                .venueName("Grand Cinema")
                .venueAddress("100 MG Road, Mumbai")
                .screenName("Audi 1")
                .seats(List.of(
                        SeatInfo.builder().rowName("F").seatNumber("F10").seatType("VIP").price(new BigDecimal("750.00")).build()
                ))
                .totalAmount(new BigDecimal("750.00"))
                .currency("INR")
                .paymentStatus("PAID")
                .paymentReference("pay_rzp_998877")
                .ticketId(501L)
                .ticketNumber("TKT-2001-999")
                .ticketStatus("CONFIRMED")
                .qrCode("TICKETHUB|TKT-2001-999|BOOKING:BK-20261012-777")
                .issuedAt(LocalDateTime.of(2026, 10, 12, 23, 30))
                .recipientEmail("john.doe@example.com")
                .recipientName("John Doe")
                .build();
    }

    @Test
    @DisplayName("1. 2nd Gmail Notification: Sends complete confirmed ticket email with all ticket & event fields")
    void testSendBookingConfirmationEmail() {
        BookingConfirmedEvent event = sampleEvent();

        emailService.sendBookingConfirmationEmail("john.doe@example.com", event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("john.doe@example.com", sent.getTo()[0]);
        assertNotNull(sent.getFrom());
        assertTrue(sent.getSubject().contains("Booking Confirmed"));
        assertTrue(sent.getText().contains("CONFIRMED"));
        assertTrue(sent.getText().contains("TKT-2001-999"));
        assertTrue(sent.getText().contains("501"));
        assertTrue(sent.getText().contains("TICKETHUB|TKT-2001-999|BOOKING:BK-20261012-777"));
        assertTrue(sent.getText().contains("BK-20261012-777"));
        assertTrue(sent.getText().contains("Interstellar IMAX"));
        assertTrue(sent.getText().contains("Grand Cinema"));
        assertTrue(sent.getText().contains("100 MG Road, Mumbai"));
        assertTrue(sent.getText().contains("F10"));
        assertTrue(sent.getText().contains("750.00 INR"));
        assertTrue(sent.getText().contains("pay_rzp_998877"));
    }

    @Test
    @DisplayName("2. 1st Gmail Notification: Sends complete payment transaction receipt email")
    void testSendPaymentSuccessEmail() {
        emailService.sendPaymentSuccessEmail("john.doe@example.com", 2001L, "pay_12345", "750.00", "INR");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("john.doe@example.com", sent.getTo()[0]);
        assertNotNull(sent.getFrom());
        assertTrue(sent.getSubject().contains("Payment Successful"));
        assertTrue(sent.getText().contains("2001"));
        assertTrue(sent.getText().contains("pay_12345"));
        assertTrue(sent.getText().contains("750.00 INR"));
    }

    @Test
    @DisplayName("3. 1st Gmail Notification via Kafka Event: Delivers full PaymentSuccessEvent transaction receipt")
    void testDeliverFullPaymentSuccessEvent() {
        PaymentSuccessEvent paymentEvent = PaymentSuccessEvent.builder()
                .paymentId(9001L)
                .bookingId(2001L)
                .userId(42L)
                .recipientEmail("john.doe@gmail.com")
                .recipientName("John Doe")
                .razorpayPaymentId("pay_rzp_998877")
                .razorpayOrderId("order_rzp_112233")
                .amount(new BigDecimal("750.00"))
                .currency("INR")
                .paymentMethod("UPI")
                .paymentStatus("SUCCESS")
                .paidAt(LocalDateTime.of(2026, 10, 12, 23, 29))
                .build();

        when(repository.findByEventKey("payment-success:9001")).thenReturn(Optional.empty());
        lenient().when(repository.saveAndFlush(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        deliveryService.deliverPaymentSuccess(paymentEvent, "john.doe@gmail.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("john.doe@gmail.com", sent.getTo()[0]);
        assertTrue(sent.getText().contains("John Doe"));
        assertTrue(sent.getText().contains("9001"));
        assertTrue(sent.getText().contains("pay_rzp_998877"));
        assertTrue(sent.getText().contains("order_rzp_112233"));
        assertTrue(sent.getText().contains("2001"));
        assertTrue(sent.getText().contains("UPI"));
        assertTrue(sent.getText().contains("750.00 INR"));
        assertTrue(sent.getText().contains("SUCCESS"));
    }

    @Test
    @DisplayName("4. Delivery Resilience: Mail failure updates notification status to FAILED")
    void testDeliveryFailureRecordedInDatabase() {
        BookingConfirmedEvent event = sampleEvent();
        doThrow(new RuntimeException("SMTP Connection Refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        when(repository.findByEventKey(anyString())).thenReturn(Optional.empty());
        lenient().when(repository.saveAndFlush(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(IllegalStateException.class, () -> deliveryService.deliverBookingConfirmation(event));

        verify(repository).save(argThat(n ->
                n.getStatus() == NotificationStatus.FAILED &&
                n.getErrorMessage().contains("SMTP Connection Refused")
        ));
    }
}

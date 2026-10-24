package org.tickethub.notificationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tickethub.notificationservice.controller.NotificationController;
import org.tickethub.notificationservice.dto.PaymentSuccessNotificationRequest;
import org.tickethub.notificationservice.entity.Notification;
import org.tickethub.notificationservice.entity.NotificationChannel;
import org.tickethub.notificationservice.entity.NotificationStatus;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;
import org.tickethub.notificationservice.event.SeatInfo;
import org.tickethub.notificationservice.exception.GlobalExceptionHandler;
import org.tickethub.notificationservice.kafka.BookingConfirmedConsumer;
import org.tickethub.notificationservice.kafka.PaymentSuccessConsumer;
import org.tickethub.notificationservice.mock.MockBookingClient;
import org.tickethub.notificationservice.repository.NotificationRepository;
import org.tickethub.notificationservice.service.EmailNotificationService;
import org.tickethub.notificationservice.service.NotificationService;
import org.tickethub.notificationservice.service.impl.EmailNotificationServiceImpl;
import org.tickethub.notificationservice.service.impl.NotificationDeliveryService;
import org.tickethub.notificationservice.service.impl.NotificationServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class NotificationControllerIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationRepository notificationRepository;

    private Map<String, Notification> notificationStore;
    private AtomicLong notificationIdSeq;

    private PaymentSuccessConsumer paymentSuccessConsumer;
    private BookingConfirmedConsumer bookingConfirmedConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        notificationStore = new LinkedHashMap<>();
        notificationIdSeq = new AtomicLong(1L);

        lenient().when(notificationRepository.findByEventKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return Optional.ofNullable(notificationStore.get(key));
        });

        lenient().when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getNotificationId() == null) {
                n.setNotificationId(notificationIdSeq.getAndIncrement());
            }
            notificationStore.put(n.getEventKey(), n);
            return n;
        });

        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getNotificationId() == null) {
                n.setNotificationId(notificationIdSeq.getAndIncrement());
            }
            notificationStore.put(n.getEventKey(), n);
            return n;
        });

        EmailNotificationService emailService = new EmailNotificationServiceImpl(mailSender);
        NotificationDeliveryService deliveryService = new NotificationDeliveryService(notificationRepository, emailService);
        NotificationService notificationService = new NotificationServiceImpl(deliveryService);
        NotificationController notificationController = new NotificationController(notificationService);

        MockBookingClient mockBookingClient = new MockBookingClient();
        paymentSuccessConsumer = new PaymentSuccessConsumer(deliveryService, mockBookingClient);
        bookingConfirmedConsumer = new BookingConfirmedConsumer(deliveryService);

        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/notifications/payment-success -> 200 OK sends 1st Gmail notification and persists SENT record")
    void testSendPaymentSuccessNotificationV1Endpoint() throws Exception {
        PaymentSuccessNotificationRequest request = new PaymentSuccessNotificationRequest(
                1001L,
                "pay_rzp_1001",
                new BigDecimal("650.00"),
                "INR",
                "customer1001@gmail.com"
        );

        mockMvc.perform(post("/api/v1/notifications/payment-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment success notification sent successfully"));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mailCaptor.capture());

        SimpleMailMessage mail = mailCaptor.getValue();
        assertEquals("customer1001@gmail.com", mail.getTo()[0]);
        assertTrue(mail.getSubject().contains("Payment Successful"));
        assertTrue(mail.getText().contains("pay_rzp_1001"));
        assertTrue(mail.getText().contains("650.00 INR"));

        Notification persisted = notificationStore.get("payment-success:pay_rzp_1001");
        assertNotNull(persisted);
        assertEquals(NotificationStatus.SENT, persisted.getStatus());
        assertEquals(NotificationChannel.EMAIL, persisted.getChannel());
        assertEquals("customer1001@gmail.com", persisted.getRecipient());
        assertEquals(1001L, persisted.getBookingId());
        assertNotNull(persisted.getSentAt());
    }

    @Test
    @DisplayName("POST /api/notifications/payment-success -> 200 OK via gateway alias path")
    void testSendPaymentSuccessNotificationAliasEndpoint() throws Exception {
        PaymentSuccessNotificationRequest request = new PaymentSuccessNotificationRequest(
                1002L,
                "pay_rzp_1002",
                new BigDecimal("1250.00"),
                "INR",
                "customer1002@gmail.com"
        );

        mockMvc.perform(post("/api/notifications/payment-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment success notification sent successfully"));

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mailCaptor.capture());

        SimpleMailMessage mail = mailCaptor.getValue();
        assertEquals("customer1002@gmail.com", mail.getTo()[0]);
        assertTrue(mail.getText().contains("pay_rzp_1002"));
        assertTrue(mail.getText().contains("1250.00 INR"));

        Notification persisted = notificationStore.get("payment-success:pay_rzp_1002");
        assertNotNull(persisted);
        assertEquals(NotificationStatus.SENT, persisted.getStatus());
    }

    @Test
    @DisplayName("PaymentSuccessConsumer.consumePaymentSuccess -> 1st Gmail notification after transaction from PaymentService with full payment info")
    void testPaymentSuccessConsumerFlow() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .paymentId(5001L)
                .bookingId(3001L)
                .userId(77L)
                .recipientEmail("buyer.one@gmail.com")
                .recipientName("Buyer One")
                .razorpayPaymentId("pay_rzp_tx_5001")
                .razorpayOrderId("order_rzp_tx_5001")
                .amount(new BigDecimal("980.00"))
                .currency("INR")
                .paymentMethod("UPI")
                .paymentStatus("SUCCESS")
                .paidAt(LocalDateTime.of(2026, 10, 13, 10, 15))
                .build();

        paymentSuccessConsumer.consumePaymentSuccess(event);

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mailCaptor.capture());

        SimpleMailMessage mail = mailCaptor.getValue();
        assertEquals("buyer.one@gmail.com", mail.getTo()[0]);
        assertTrue(mail.getSubject().contains("Payment Successful"));
        assertTrue(mail.getText().contains("Buyer One"));
        assertTrue(mail.getText().contains("5001"));
        assertTrue(mail.getText().contains("pay_rzp_tx_5001"));
        assertTrue(mail.getText().contains("order_rzp_tx_5001"));
        assertTrue(mail.getText().contains("3001"));
        assertTrue(mail.getText().contains("UPI"));
        assertTrue(mail.getText().contains("980.00 INR"));
        assertTrue(mail.getText().contains("SUCCESS"));

        Notification persisted = notificationStore.get("payment-success:5001");
        assertNotNull(persisted);
        assertEquals(NotificationStatus.SENT, persisted.getStatus());
        assertEquals("pay_rzp_tx_5001", persisted.getProviderReference());
    }

    @Test
    @DisplayName("BookingConfirmedConsumer.consumeBookingConfirmed -> 2nd Gmail notification when ticket status is CONFIRMED with complete generated ticket info")
    void testBookingConfirmedConsumerFlow() {
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(3001L)
                .bookingReference("BK-20261013-3001")
                .userId(77L)
                .recipientName("Buyer One")
                .recipientEmail("buyer.one@gmail.com")
                .eventId(15L)
                .eventTitle("Oppenheimer 70mm IMAX")
                .showId(45L)
                .showDate(LocalDate.of(2026, 11, 15))
                .startTime(LocalTime.of(18, 30))
                .endTime(LocalTime.of(21, 30))
                .venueName("PVR Phoenix Lower Parel")
                .venueAddress("High Street Phoenix, Mumbai")
                .screenName("IMAX Screen 1")
                .seats(List.of(
                        SeatInfo.builder().rowName("G").seatNumber("G11").seatType("PRIME").price(new BigDecimal("490.00")).build(),
                        SeatInfo.builder().rowName("G").seatNumber("G12").seatType("PRIME").price(new BigDecimal("490.00")).build()
                ))
                .totalAmount(new BigDecimal("980.00"))
                .currency("INR")
                .paymentStatus("PAID")
                .paymentReference("pay_rzp_tx_5001")
                .ticketId(801L)
                .ticketNumber("TKT-3001-801")
                .ticketStatus("CONFIRMED")
                .qrCode("TICKETHUB|TKT-3001-801|BOOKING:BK-20261013-3001")
                .issuedAt(LocalDateTime.of(2026, 10, 13, 10, 16))
                .build();

        bookingConfirmedConsumer.consumeBookingConfirmed(event);

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mailCaptor.capture());

        SimpleMailMessage mail = mailCaptor.getValue();
        assertEquals("buyer.one@gmail.com", mail.getTo()[0]);
        assertTrue(mail.getSubject().contains("Booking Confirmed"));
        String body = mail.getText();
        assertTrue(body.contains("CONFIRMED"));
        assertTrue(body.contains("TKT-3001-801"));
        assertTrue(body.contains("801"));
        assertTrue(body.contains("TICKETHUB|TKT-3001-801|BOOKING:BK-20261013-3001"));
        assertTrue(body.contains("Oppenheimer 70mm IMAX"));
        assertTrue(body.contains("2026-11-15"));
        assertTrue(body.contains("18:30"));
        assertTrue(body.contains("21:30"));
        assertTrue(body.contains("PVR Phoenix Lower Parel"));
        assertTrue(body.contains("High Street Phoenix, Mumbai"));
        assertTrue(body.contains("IMAX Screen 1"));
        assertTrue(body.contains("G11"));
        assertTrue(body.contains("G12"));
        assertTrue(body.contains("980.00 INR"));
        assertTrue(body.contains("pay_rzp_tx_5001"));

        Notification persisted = notificationStore.get("booking-confirmed:3001");
        assertNotNull(persisted);
        assertEquals(NotificationStatus.SENT, persisted.getStatus());
        assertEquals("TKT-3001-801", persisted.getProviderReference());
        assertNotNull(persisted.getSentAt());
    }
}

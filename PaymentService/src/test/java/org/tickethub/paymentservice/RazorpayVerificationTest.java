package org.tickethub.paymentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tickethub.paymentservice.client.BookingServiceGateway;
import org.tickethub.paymentservice.dto.BookingResponse;
import org.tickethub.paymentservice.entity.Payment;
import org.tickethub.paymentservice.entity.PaymentWebhookEvent;
import org.tickethub.paymentservice.entity.enums.PaymentMethod;
import org.tickethub.paymentservice.entity.enums.PaymentStatus;
import org.tickethub.paymentservice.event.PaymentSuccessEvent;
import org.tickethub.paymentservice.kafka.PaymentEventProducer;
import org.tickethub.paymentservice.provider.PaymentProvider;
import org.tickethub.paymentservice.repository.PaymentRepository;
import org.tickethub.paymentservice.repository.PaymentWebhookEventRepository;
import org.tickethub.paymentservice.service.impl.PaymentServiceImpl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RazorpayVerificationTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private BookingServiceGateway bookingServiceGateway;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentServiceImpl paymentService;

    private final String webhookSecret = "test_webhook_secret_12345";
    private final String testOrderId = "order_rzp_test_1001";
    private final String testPaymentId = "pay_rzp_test_9001";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                paymentWebhookEventRepository,
                paymentProvider,
                paymentEventProducer,
                bookingServiceGateway,
                objectMapper
        );
    }

    private String generateSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String createWebhookPayload(String eventType, String eventId, String orderId, String paymentId) {
        return """
                {
                  "entity": "event",
                  "account_id": "acc_test123",
                  "event": "%s",
                  "id": "%s",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "order_id": "%s",
                        "amount": 50000,
                        "currency": "INR",
                        "status": "captured"
                      }
                    }
                  }
                }
                """.formatted(eventType, eventId, paymentId, orderId);
    }

    @Test
    @DisplayName("1. Valid Signed Webhook: Processes captured payment and confirms booking")
    void testValidSignedCapturedWebhook() throws Exception {
        String payload = createWebhookPayload("payment.captured", "evt_101", testOrderId, testPaymentId);
        String signature = generateSignature(payload, webhookSecret);

        Payment payment = Payment.builder()
                .id(1L)
                .bookingId(100L)
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.PENDING)
                .razorpayOrderId(testOrderId)
                .idempotencyKey("idem-1001")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(paymentProvider.verifyWebhookSignature(payload, signature)).thenReturn(true);
        when(paymentWebhookEventRepository.existsByEventId("evt_101")).thenReturn(false);
        when(paymentRepository.findByRazorpayOrderId(testOrderId)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse booking = BookingResponse.builder()
                .bookingId(100L)
                .userId(1L)
                .customerEmail("john.doe@gmail.com")
                .customerName("John Doe")
                .totalAmount(new BigDecimal("500.00"))
                .status("PENDING_PAYMENT")
                .build();
        when(bookingServiceGateway.getBooking(100L)).thenReturn(booking);

        paymentService.processWebhook(payload, signature);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(testPaymentId, payment.getRazorpayPaymentId());
        verify(bookingServiceGateway).markPaymentSuccess(100L, testPaymentId);

        ArgumentCaptor<PaymentSuccessEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSuccessEvent.class);
        verify(paymentEventProducer).publishPaymentSuccess(eventCaptor.capture());
        PaymentSuccessEvent publishedEvent = eventCaptor.getValue();
        assertEquals(1L, publishedEvent.getPaymentId());
        assertEquals(100L, publishedEvent.getBookingId());
        assertEquals(1L, publishedEvent.getUserId());
        assertEquals("john.doe@gmail.com", publishedEvent.getRecipientEmail());
        assertEquals("John Doe", publishedEvent.getRecipientName());
        assertEquals(testPaymentId, publishedEvent.getRazorpayPaymentId());
        assertEquals(testOrderId, publishedEvent.getRazorpayOrderId());
        assertEquals(new BigDecimal("500.00"), publishedEvent.getAmount());
        assertEquals("INR", publishedEvent.getCurrency());
        assertEquals("UPI", publishedEvent.getPaymentMethod());
        assertEquals("SUCCESS", publishedEvent.getPaymentStatus());
        assertNotNull(publishedEvent.getPaidAt());
    }

    @Test
    @DisplayName("2. Security: Invalid Webhook Signature is Rejected")
    void testInvalidSignatureRejected() {
        String payload = createWebhookPayload("payment.captured", "evt_102", testOrderId, testPaymentId);
        String invalidSignature = "invalid_signature_hex_12345";

        when(paymentProvider.verifyWebhookSignature(payload, invalidSignature)).thenReturn(false);

        assertThrows(SecurityException.class, () -> paymentService.processWebhook(payload, invalidSignature));
        verify(paymentRepository, never()).findByRazorpayOrderId(anyString());
    }

    @Test
    @DisplayName("3. Failure: Webhook payment.failed marks payment FAILED and releases seat locks")
    void testPaymentFailedWebhook() throws Exception {
        String payload = createWebhookPayload("payment.failed", "evt_103", testOrderId, testPaymentId);
        String signature = generateSignature(payload, webhookSecret);

        Payment payment = Payment.builder()
                .id(2L)
                .bookingId(101L)
                .amount(new BigDecimal("350.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .razorpayOrderId(testOrderId)
                .idempotencyKey("idem-1002")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(paymentProvider.verifyWebhookSignature(payload, signature)).thenReturn(true);
        when(paymentWebhookEventRepository.existsByEventId("evt_103")).thenReturn(false);
        when(paymentRepository.findByRazorpayOrderId(testOrderId)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.processWebhook(payload, signature);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(bookingServiceGateway).markPaymentFailure(101L, testPaymentId);
        verify(paymentEventProducer, never()).publishPaymentSuccess(any());
    }

    @Test
    @DisplayName("4. Duplicate Webhook: Same event ID is safely ignored idempotently")
    void testDuplicateWebhookIgnored() throws Exception {
        String payload = createWebhookPayload("payment.captured", "evt_duplicate_999", testOrderId, testPaymentId);
        String signature = generateSignature(payload, webhookSecret);

        when(paymentProvider.verifyWebhookSignature(payload, signature)).thenReturn(true);
        when(paymentWebhookEventRepository.existsByEventId("evt_duplicate_999")).thenReturn(true);

        paymentService.processWebhook(payload, signature);

        verify(paymentRepository, never()).findByRazorpayOrderId(anyString());
        verify(bookingServiceGateway, never()).markPaymentSuccess(anyLong(), anyString());
    }

    @Test
    @DisplayName("5. Recovery Scenario: Captured payment for EXPIRED booking triggers automated refund")
    void testLatePaymentCapturedForExpiredBookingTriggersRefund() throws Exception {
        String payload = createWebhookPayload("payment.captured", "evt_105", testOrderId, testPaymentId);
        String signature = generateSignature(payload, webhookSecret);

        Payment payment = Payment.builder()
                .id(3L)
                .bookingId(102L)
                .amount(new BigDecimal("600.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.NET_BANKING)
                .status(PaymentStatus.PENDING)
                .razorpayOrderId(testOrderId)
                .idempotencyKey("idem-1003")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(paymentProvider.verifyWebhookSignature(payload, signature)).thenReturn(true);
        when(paymentWebhookEventRepository.existsByEventId("evt_105")).thenReturn(false);
        when(paymentRepository.findByRazorpayOrderId(testOrderId)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse expiredBooking = BookingResponse.builder()
                .bookingId(102L)
                .userId(1L)
                .totalAmount(new BigDecimal("600.00"))
                .status("EXPIRED")
                .build();
        when(bookingServiceGateway.getBooking(102L)).thenReturn(expiredBooking);

        paymentService.processWebhook(payload, signature);

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(paymentProvider).refund(testPaymentId, new BigDecimal("600.00"));
        verify(bookingServiceGateway, never()).markPaymentSuccess(anyLong(), anyString());
    }

    @Test
    @DisplayName("6. Direct Payment Confirmation: Confirms payment and publishes full PaymentSuccessEvent")
    void testConfirmPaymentPublishesCompleteTransactionEvent() {
        Payment payment = Payment.builder()
                .id(10L)
                .bookingId(500L)
                .amount(new BigDecimal("1200.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .razorpayOrderId("order_sim_500")
                .idempotencyKey("idem-500")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        BookingResponse booking = BookingResponse.builder()
                .bookingId(500L)
                .userId(42L)
                .customerEmail("alice@gmail.com")
                .customerName("Alice")
                .totalAmount(new BigDecimal("1200.00"))
                .status("PENDING_PAYMENT")
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(bookingServiceGateway.getBooking(500L)).thenReturn(booking);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        var response = paymentService.confirmPayment(10L, null);

        assertEquals("SUCCESS", response.getStatus());
        verify(bookingServiceGateway).markPaymentSuccess(eq(500L), anyString());

        ArgumentCaptor<PaymentSuccessEvent> captor = ArgumentCaptor.forClass(PaymentSuccessEvent.class);
        verify(paymentEventProducer).publishPaymentSuccess(captor.capture());
        PaymentSuccessEvent event = captor.getValue();
        assertEquals("alice@gmail.com", event.getRecipientEmail());
        assertEquals("Alice", event.getRecipientName());
        assertEquals("CARD", event.getPaymentMethod());
        assertEquals("SUCCESS", event.getPaymentStatus());
    }
}

package org.tickethub.paymentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tickethub.paymentservice.client.BookingServiceGateway;
import org.tickethub.paymentservice.config.RazorpayConfig;
import org.tickethub.paymentservice.controller.PaymentController;
import org.tickethub.paymentservice.dto.BookingResponse;
import org.tickethub.paymentservice.dto.PaymentRequest;
import org.tickethub.paymentservice.dto.PaymentVerificationRequest;
import org.tickethub.paymentservice.entity.Payment;
import org.tickethub.paymentservice.entity.PaymentWebhookEvent;
import org.tickethub.paymentservice.entity.enums.PaymentMethod;
import org.tickethub.paymentservice.entity.enums.PaymentStatus;
import org.tickethub.paymentservice.event.PaymentSuccessEvent;
import org.tickethub.paymentservice.exception.GlobalExceptionHandler;
import org.tickethub.paymentservice.kafka.PaymentEventProducer;
import org.tickethub.paymentservice.provider.RazorpayPaymentProvider;
import org.tickethub.paymentservice.repository.PaymentRepository;
import org.tickethub.paymentservice.repository.PaymentWebhookEventRepository;
import org.tickethub.paymentservice.service.impl.PaymentServiceImpl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private BookingServiceGateway bookingServiceGateway;

    private Map<Long, Payment> paymentStore;
    private Set<String> processedWebhookEvents;
    private AtomicLong paymentIdSeq;

    private static final String WEBHOOK_SECRET = "rzp_webhook_secret_integration_test_32chars";
    private static final String KEY_ID = "rzp_test_mock_key_id_123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        paymentStore = new LinkedHashMap<>();
        processedWebhookEvents = new HashSet<>();
        paymentIdSeq = new AtomicLong(100L);

        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(paymentIdSeq.incrementAndGet());
                p.setCreatedAt(LocalDateTime.now());
            }
            p.setUpdatedAt(LocalDateTime.now());
            paymentStore.put(p.getId(), p);
            return p;
        });

        lenient().when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(paymentIdSeq.incrementAndGet());
                p.setCreatedAt(LocalDateTime.now());
            }
            p.setUpdatedAt(LocalDateTime.now());
            paymentStore.put(p.getId(), p);
            return p;
        });

        lenient().when(paymentRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(paymentStore.get(id));
        });

        lenient().when(paymentRepository.findByBookingId(anyLong())).thenAnswer(inv -> {
            Long bookingId = inv.getArgument(0);
            return paymentStore.values().stream()
                    .filter(p -> Objects.equals(p.getBookingId(), bookingId))
                    .findFirst();
        });

        lenient().when(paymentRepository.findByIdempotencyKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return paymentStore.values().stream()
                    .filter(p -> Objects.equals(p.getIdempotencyKey(), key))
                    .findFirst();
        });

        lenient().when(paymentRepository.findByRazorpayOrderId(anyString())).thenAnswer(inv -> {
            String orderId = inv.getArgument(0);
            return paymentStore.values().stream()
                    .filter(p -> Objects.equals(p.getRazorpayOrderId(), orderId))
                    .findFirst();
        });

        lenient().when(paymentRepository.findAll()).thenAnswer(inv -> new ArrayList<>(paymentStore.values()));

        lenient().when(paymentWebhookEventRepository.existsByEventId(anyString())).thenAnswer(inv -> {
            String eventId = inv.getArgument(0);
            return processedWebhookEvents.contains(eventId);
        });

        lenient().when(paymentWebhookEventRepository.saveAndFlush(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent evt = inv.getArgument(0);
            processedWebhookEvents.add(evt.getEventId());
            return evt;
        });

        lenient().when(paymentWebhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent evt = inv.getArgument(0);
            processedWebhookEvents.add(evt.getEventId());
            return evt;
        });

        RazorpayConfig razorpayConfig = new RazorpayConfig();
        razorpayConfig.setKeyId("mock_key_id");
        razorpayConfig.setKeySecret("mock_key_secret");
        razorpayConfig.setWebhookSecret(WEBHOOK_SECRET);

        RazorpayPaymentProvider paymentProvider = new RazorpayPaymentProvider(razorpayConfig);

        PaymentServiceImpl paymentService = new PaymentServiceImpl(
                paymentRepository,
                paymentWebhookEventRepository,
                paymentProvider,
                paymentEventProducer,
                bookingServiceGateway,
                objectMapper
        );
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", KEY_ID);

        PaymentController paymentController = new PaymentController(paymentService, bookingServiceGateway);

        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private BookingResponse createSampleBooking(Long bookingId, Long userId, BigDecimal amount) {
        return BookingResponse.builder()
                .bookingId(bookingId)
                .bookingReference("BK-" + bookingId)
                .userId(userId)
                .customerEmail("customer" + userId + "@gmail.com")
                .customerName("Customer " + userId)
                .totalAmount(amount)
                .currency("INR")
                .status("PENDING_PAYMENT")
                .build();
    }

    private String signWebhook(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("POST /api/payments -> 201 Created returning razorpayOrderId & razorpayKeyId; 403 Forbidden on X-User-Id mismatch; idempotent on same idempotencyKey or bookingId")
    void testCreatePaymentEndpointAndIdempotencyAndForbidden() throws Exception {
        BookingResponse booking = createSampleBooking(501L, 10L, new BigDecimal("850.00"));
        when(bookingServiceGateway.getBooking(501L)).thenReturn(booking);

        PaymentRequest request = PaymentRequest.builder()
                .bookingId(501L)
                .amount(new BigDecimal("850.00"))
                .currency("INR")
                .idempotencyKey("idem-key-501")
                .paymentMethod(PaymentMethod.UPI)
                .build();

        // 1. 403 Forbidden when X-User-Id mismatches booking owner (userId=10)
        mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", "999")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // 2. 201 Created for owner returning razorpayOrderId & razorpayKeyId
        String responseJson = mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", "10")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.bookingId").value(501))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.razorpayOrderId", org.hamcrest.Matchers.startsWith("order_mock_")))
                .andExpect(jsonPath("$.data.razorpayKeyId").value(KEY_ID))
                .andReturn().getResponse().getContentAsString();

        String createdOrderId = objectMapper.readTree(responseJson).path("data").path("razorpayOrderId").asText();
        long createdPaymentId = objectMapper.readTree(responseJson).path("data").path("paymentId").asLong();

        // 3. Idempotent on same idempotencyKey
        mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", "10")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentId").value(createdPaymentId))
                .andExpect(jsonPath("$.data.razorpayOrderId").value(createdOrderId));

        // 4. Idempotent on same bookingId with a different idempotencyKey while PENDING
        PaymentRequest secondKeySameBooking = PaymentRequest.builder()
                .bookingId(501L)
                .amount(new BigDecimal("850.00"))
                .currency("INR")
                .idempotencyKey("idem-key-501-retry")
                .paymentMethod(PaymentMethod.UPI)
                .build();

        mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", "10")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondKeySameBooking)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentId").value(createdPaymentId))
                .andExpect(jsonPath("$.data.razorpayOrderId").value(createdOrderId));
    }

    @Test
    @DisplayName("GET /api/payments/{paymentId} -> 200 OK for owner; 403 Forbidden for different customer; 404 Not Found when paymentId does not exist")
    void testGetPaymentByIdEndpoint() throws Exception {
        BookingResponse booking = createSampleBooking(502L, 20L, new BigDecimal("400.00"));
        when(bookingServiceGateway.getBooking(502L)).thenReturn(booking);

        Payment saved = paymentRepository.save(Payment.builder()
                .bookingId(502L)
                .amount(new BigDecimal("400.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("idem-502")
                .razorpayOrderId("order_mock_502")
                .build());

        // 200 OK for owner
        mockMvc.perform(get("/api/payments/{paymentId}", saved.getId())
                        .header("X-User-Id", "20")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(saved.getId()))
                .andExpect(jsonPath("$.data.bookingId").value(502));

        // 403 Forbidden for different customer
        mockMvc.perform(get("/api/payments/{paymentId}", saved.getId())
                        .header("X-User-Id", "99")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());

        // 404 Not Found when paymentId does not exist
        mockMvc.perform(get("/api/payments/{paymentId}", 99999L)
                        .header("X-User-Id", "20")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/payments/booking/{bookingId} -> 200 OK for owner; 404 Not Found when bookingId has no payment")
    void testGetPaymentByBookingIdEndpoint() throws Exception {
        BookingResponse booking = createSampleBooking(503L, 30L, new BigDecimal("600.00"));
        when(bookingServiceGateway.getBooking(503L)).thenReturn(booking);

        Payment saved = paymentRepository.save(Payment.builder()
                .bookingId(503L)
                .amount(new BigDecimal("600.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("idem-503")
                .razorpayOrderId("order_mock_503")
                .build());

        // 200 OK for owner
        mockMvc.perform(get("/api/payments/booking/{bookingId}", 503L)
                        .header("X-User-Id", "30")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(saved.getId()))
                .andExpect(jsonPath("$.data.bookingId").value(503));

        // 404 Not Found when bookingId has no payment
        mockMvc.perform(get("/api/payments/booking/{bookingId}", 88888L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/payments -> 200 OK for ADMIN; 403 Forbidden for CUSTOMER")
    void testGetAllPaymentsRoleAccess() throws Exception {
        paymentRepository.save(Payment.builder()
                .bookingId(504L)
                .amount(new BigDecimal("300.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("idem-504")
                .razorpayOrderId("order_mock_504")
                .build());

        // 200 OK for ADMIN
        mockMvc.perform(get("/api/payments")
                        .header("X-Authenticated-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 403 Forbidden for CUSTOMER
        mockMvc.perform(get("/api/payments")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/payments/{paymentId}/status -> 200 OK for owner; 403 Forbidden for different customer")
    void testCheckPaymentStatusEndpoint() throws Exception {
        BookingResponse booking = createSampleBooking(505L, 50L, new BigDecimal("500.00"));
        when(bookingServiceGateway.getBooking(505L)).thenReturn(booking);

        Payment saved = paymentRepository.save(Payment.builder()
                .bookingId(505L)
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.NET_BANKING)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("idem-505")
                .razorpayOrderId("order_mock_505")
                .build());

        // 200 OK for owner
        mockMvc.perform(get("/api/payments/{paymentId}/status", saved.getId())
                        .header("X-User-Id", "50")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 403 Forbidden for different customer
        mockMvc.perform(get("/api/payments/{paymentId}/status", saved.getId())
                        .header("X-User-Id", "777")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/payments/webhook -> 200 OK valid signature, marks SUCCESS, calls markPaymentSuccess, publishes PaymentSuccessEvent; 400 on invalid signature; idempotent on duplicate X-Razorpay-Event-Id")
    void testWebhookEndpointValidInvalidAndDuplicate() throws Exception {
        BookingResponse booking = createSampleBooking(506L, 60L, new BigDecimal("750.00"));
        when(bookingServiceGateway.getBooking(506L)).thenReturn(booking);

        Payment saved = paymentRepository.save(Payment.builder()
                .bookingId(506L)
                .amount(new BigDecimal("750.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("idem-506")
                .razorpayOrderId("order_rzp_webhook_506")
                .build());

        String payload = """
                {
                  "entity": "event",
                  "event": "payment.captured",
                  "id": "evt_body_506",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_rzp_webhook_506",
                        "order_id": "order_rzp_webhook_506",
                        "amount": 75000,
                        "currency": "INR",
                        "status": "captured"
                      }
                    }
                  }
                }
                """;
        String validSignature = signWebhook(payload);

        // 1. 400 Bad Request on invalid signature
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Razorpay-Signature", "invalid_signature_value")
                        .header("X-Razorpay-Event-Id", "evt_header_invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        // 2. 200 OK with valid X-Razorpay-Signature and X-Razorpay-Event-Id
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Razorpay-Signature", validSignature)
                        .header("X-Razorpay-Event-Id", "evt_header_506")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertEquals(PaymentStatus.SUCCESS, paymentStore.get(saved.getId()).getStatus());
        verify(bookingServiceGateway, times(1)).markPaymentSuccess(506L, "pay_rzp_webhook_506");

        ArgumentCaptor<PaymentSuccessEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSuccessEvent.class);
        verify(paymentEventProducer, times(1)).publishPaymentSuccess(eventCaptor.capture());
        PaymentSuccessEvent event = eventCaptor.getValue();
        assertEquals("customer60@gmail.com", event.getRecipientEmail());
        assertEquals("pay_rzp_webhook_506", event.getRazorpayPaymentId());
        assertEquals("order_rzp_webhook_506", event.getRazorpayOrderId());
        assertEquals(new BigDecimal("750.00"), event.getAmount());
        assertEquals("INR", event.getCurrency());
        assertEquals("UPI", event.getPaymentMethod());
        assertNotNull(event.getPaidAt());

        // 3. Idempotent on duplicate X-Razorpay-Event-Id
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Razorpay-Signature", validSignature)
                        .header("X-Razorpay-Event-Id", "evt_header_506")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(bookingServiceGateway, times(1)).markPaymentSuccess(anyLong(), anyString());
        verify(paymentEventProducer, times(1)).publishPaymentSuccess(any());
    }

    @Test
    @DisplayName("POST /api/payments/{paymentId}/confirm -> 200 OK confirms payment and publishes PaymentSuccessEvent")
    void testConfirmPaymentEndpoint() throws Exception {
        BookingResponse booking = createSampleBooking(507L, 70L, new BigDecimal("900.00"));
        when(bookingServiceGateway.getBooking(507L)).thenReturn(booking);

        Payment saved = paymentRepository.save(Payment.builder()
                .bookingId(507L)
                .amount(new BigDecimal("900.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("idem-507")
                .razorpayOrderId("order_mock_507")
                .build());

        PaymentVerificationRequest confirmReq = PaymentVerificationRequest.builder()
                .razorpayOrderId("order_mock_507")
                .razorpayPaymentId("pay_confirmed_507")
                .razorpaySignature("sig_valid_507")
                .build();

        mockMvc.perform(post("/api/payments/{paymentId}/confirm", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        verify(bookingServiceGateway).markPaymentSuccess(507L, "pay_confirmed_507");
        verify(paymentEventProducer).publishPaymentSuccess(argThat(evt ->
                "customer70@gmail.com".equals(evt.getRecipientEmail())
                        && "pay_confirmed_507".equals(evt.getRazorpayPaymentId())
                        && "order_mock_507".equals(evt.getRazorpayOrderId())
                        && new BigDecimal("900.00").equals(evt.getAmount())
                        && "INR".equals(evt.getCurrency())
                        && "CARD".equals(evt.getPaymentMethod())
                        && evt.getPaidAt() != null
        ));
    }

    @Test
    @DisplayName("POST /api/payments/verify -> 200 OK verifies and confirms payment")
    void testVerifyPaymentEndpoint() throws Exception {
        BookingResponse booking = createSampleBooking(508L, 80L, new BigDecimal("1100.00"));
        when(bookingServiceGateway.getBooking(508L)).thenReturn(booking);

        Payment saved = paymentRepository.save(Payment.builder()
                .bookingId(508L)
                .amount(new BigDecimal("1100.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("idem-508")
                .razorpayOrderId("order_mock_508")
                .build());

        PaymentVerificationRequest verifyReq = PaymentVerificationRequest.builder()
                .paymentId(saved.getId())
                .razorpayOrderId("order_mock_508")
                .razorpayPaymentId("pay_verified_508")
                .razorpaySignature("sig_valid_508")
                .build();

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        verify(bookingServiceGateway).markPaymentSuccess(508L, "pay_verified_508");
        verify(paymentEventProducer).publishPaymentSuccess(any(PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("POST /api/payments/{paymentId}/cancel -> 200 OK cancels pending payment")
    void testCancelPaymentEndpoint() throws Exception {
        Payment saved = paymentRepository.save(Payment.builder()
                .bookingId(509L)
                .amount(new BigDecimal("450.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.WALLET)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("idem-509")
                .razorpayOrderId("order_mock_509")
                .build());

        mockMvc.perform(post("/api/payments/{paymentId}/cancel", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertEquals(PaymentStatus.CANCELLED, paymentStore.get(saved.getId()).getStatus());
        verify(bookingServiceGateway).markPaymentFailure(509L, "payment-cancelled");
    }
}

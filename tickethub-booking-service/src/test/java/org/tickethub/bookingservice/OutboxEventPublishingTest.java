package org.tickethub.bookingservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.tickethub.bookingservice.entity.OutboxMessage;
import org.tickethub.bookingservice.event.BookingConfirmedEvent;
import org.tickethub.bookingservice.kafka.BookingEventProducer;
import org.tickethub.bookingservice.repository.OutboxMessageRepository;
import org.tickethub.bookingservice.scheduler.OutboxPublisherScheduler;
import org.tickethub.bookingservice.service.OutboxService;
import org.tickethub.bookingservice.service.impl.OutboxServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxEventPublishingTest {

    @Mock
    private OutboxMessageRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private OutboxService outboxService;
    private OutboxPublisherScheduler scheduler;
    private BookingEventProducer producer;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxServiceImpl(outboxRepository, objectMapper);
        scheduler = new OutboxPublisherScheduler(outboxService, kafkaTemplate, objectMapper);
        producer = new BookingEventProducer(kafkaTemplate, outboxService);
    }

    private BookingConfirmedEvent sampleEvent() {
        return BookingConfirmedEvent.builder()
                .bookingId(101L)
                .bookingReference("BK-20261012-001")
                .userId(1L)
                .movieTitle("Inception")
                .date(LocalDate.of(2026, 10, 15))
                .time(LocalTime.of(19, 30))
                .venue("IMAX Grand")
                .screen("Screen 1")
                .seats(List.of())
                .totalAmount(new BigDecimal("450.00"))
                .currency("INR")
                .paymentStatus("SUCCESS")
                .recipientEmail("customer@tickethub.com")
                .build();
    }

    @Test
    @DisplayName("1. Outbox Persistence: Event is correctly saved to outbox_messages table")
    void testOutboxEventPersistence() {
        BookingConfirmedEvent event = sampleEvent();

        when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(inv -> {
            OutboxMessage msg = inv.getArgument(0);
            msg.setId(1L);
            msg.setCreatedAt(LocalDateTime.now());
            msg.setUpdatedAt(LocalDateTime.now());
            return msg;
        });

        OutboxMessage saved = outboxService.saveEvent("BOOKING", "101", "BOOKING_CONFIRMED", "booking-confirmed", event);

        assertNotNull(saved);
        assertEquals("BOOKING", saved.getAggregateType());
        assertEquals("101", saved.getAggregateId());
        assertEquals("PENDING", saved.getStatus());
        assertTrue(saved.getPayload().contains("BK-20261012-001"));
        verify(outboxRepository).save(any(OutboxMessage.class));
    }

    @Test
    @DisplayName("2. Kafka Unavailable: Send failure marks outbox message as FAILED for retry")
    void testKafkaUnavailableMarksFailed() throws Exception {
        BookingConfirmedEvent event = sampleEvent();

        CompletableFuture<SendResult<String, BookingConfirmedEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka Broker Not Available"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        when(outboxRepository.findById(1L)).thenReturn(Optional.of(
                OutboxMessage.builder().id(1L).status("PENDING").retryCount(0).build()
        ));

        producer.sendDirect(event, 1L);

        verify(outboxRepository).save(argThat(msg ->
                "FAILED".equals(msg.getStatus()) && msg.getRetryCount() == 1
        ));
    }

    @Test
    @DisplayName("3. Recovery: Scheduler processes pending outbox message and publishes to Kafka")
    void testSchedulerRecoversAndPublishesPendingMessage() throws Exception {
        BookingConfirmedEvent event = sampleEvent();
        String jsonPayload = objectMapper.writeValueAsString(event);

        OutboxMessage pendingMsg = OutboxMessage.builder()
                .id(5L)
                .aggregateType("BOOKING")
                .aggregateId("101")
                .eventType("BOOKING_CONFIRMED")
                .topic("booking-confirmed")
                .payload(jsonPayload)
                .status("PENDING")
                .retryCount(1)
                .build();

        when(outboxRepository.findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(anyCollection(), eq(10)))
                .thenReturn(List.of(pendingMsg));

        CompletableFuture<SendResult<String, BookingConfirmedEvent>> successFuture = new CompletableFuture<>();
        successFuture.complete(null);
        when(kafkaTemplate.send(eq("booking-confirmed"), eq("101"), any(BookingConfirmedEvent.class)))
                .thenReturn(successFuture);

        when(outboxRepository.findById(5L)).thenReturn(Optional.of(pendingMsg));

        scheduler.processOutboxMessages();

        verify(kafkaTemplate).send(eq("booking-confirmed"), eq("101"), any(BookingConfirmedEvent.class));
        verify(outboxRepository).save(argThat(msg -> "PUBLISHED".equals(msg.getStatus())));
    }

    @Test
    @DisplayName("4. Dead-Letter Transition: 10th failure marks message DEAD_LETTER so it cannot starve newer PENDING messages")
    void testTenFailuresTransitionToDeadLetter() {
        when(outboxRepository.findById(9L)).thenReturn(Optional.of(
                OutboxMessage.builder().id(9L).status("FAILED").retryCount(9).build()
        ));

        outboxService.markFailed(9L, "Broker timeout");

        verify(outboxRepository).save(argThat(msg ->
                "DEAD_LETTER".equals(msg.getStatus()) && msg.getRetryCount() == 10
        ));
    }
}

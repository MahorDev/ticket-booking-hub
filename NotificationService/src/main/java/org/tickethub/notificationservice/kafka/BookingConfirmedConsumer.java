package org.tickethub.notificationservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.service.impl.NotificationDeliveryService;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmedConsumer {
    private final NotificationDeliveryService deliveryService;

    @KafkaListener(topics = "booking-confirmed", groupId = "notification-booking-group", containerFactory = "bookingConfirmedKafkaListenerContainerFactory")
    public void consumeBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Booking confirmation event received bookingId={}", event.getBookingId());
        deliveryService.deliverBookingConfirmation(event);
    }
}

package org.tickethub.notificationservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;
import org.tickethub.notificationservice.mock.MockBookingClient;
import org.tickethub.notificationservice.service.impl.NotificationDeliveryService;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSuccessConsumer {
    private final NotificationDeliveryService deliveryService;
    private final MockBookingClient mockBookingClient;

    @KafkaListener(topics = "payment-success", groupId = "notification-group")
    public void consumePaymentSuccess(PaymentSuccessEvent event) {
        log.info("Payment success event received paymentId={} bookingId={}", event.getPaymentId(), event.getBookingId());
        String email = event.getRecipientEmail();
        if (email == null || email.isBlank()) {
            email = mockBookingClient.getUserEmail(event.getBookingId());
        }
        deliveryService.deliverPaymentSuccess(event, email);
    }
}

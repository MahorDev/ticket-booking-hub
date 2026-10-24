package org.tickethub.notificationservice.service.impl;

import org.springframework.stereotype.Service;
import org.tickethub.notificationservice.dto.NotificationResponse;
import org.tickethub.notificationservice.dto.PaymentSuccessNotificationRequest;
import org.tickethub.notificationservice.service.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationDeliveryService deliveryService;

    public NotificationServiceImpl(NotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Override
    public NotificationResponse sendPaymentSuccessNotification(PaymentSuccessNotificationRequest request) {
        String eventKey = "payment-success:" + request.paymentId();
        deliveryService.deliverPaymentSuccess(
                eventKey,
                request.bookingId(),
                request.email(),
                request.paymentId(),
                request.amount().toPlainString(),
                request.currency()
        );

        return new NotificationResponse("Payment success notification sent successfully");
    }
}
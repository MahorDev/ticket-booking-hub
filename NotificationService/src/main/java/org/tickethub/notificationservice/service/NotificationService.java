package org.tickethub.notificationservice.service;

import org.tickethub.notificationservice.dto.PaymentSuccessNotificationRequest;
import org.tickethub.notificationservice.dto.NotificationResponse;

public interface NotificationService {

    NotificationResponse sendPaymentSuccessNotification(PaymentSuccessNotificationRequest request);
}
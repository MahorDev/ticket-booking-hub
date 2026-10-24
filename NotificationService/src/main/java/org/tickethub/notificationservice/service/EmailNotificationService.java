package org.tickethub.notificationservice.service;

import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;

public interface EmailNotificationService {

    void sendPaymentSuccessEmail(String email, Long bookingId, String paymentId, String amount, String currency);

    void sendPaymentSuccessEmail(String email, PaymentSuccessEvent event);

    void sendBookingConfirmationEmail(
            String email,
            BookingConfirmedEvent event);
}
package org.tickethub.notificationservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tickethub.notificationservice.dto.NotificationResponse;
import org.tickethub.notificationservice.dto.PaymentSuccessNotificationRequest;
import org.tickethub.notificationservice.service.NotificationService;

@RestController
@RequestMapping({"/api/v1/notifications", "/api/notifications"})
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/payment-success")
    @ResponseStatus(HttpStatus.OK)
    public NotificationResponse sendPaymentSuccessNotification(@Valid @RequestBody PaymentSuccessNotificationRequest request) {

        return notificationService.sendPaymentSuccessNotification(request);
    }
}
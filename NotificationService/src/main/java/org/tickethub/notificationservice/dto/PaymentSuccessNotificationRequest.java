package org.tickethub.notificationservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentSuccessNotificationRequest(

        @NotNull Long bookingId,

        @NotBlank String paymentId,

        @NotNull BigDecimal amount,

        @NotBlank String currency,

        @NotBlank @Email String email) {
}
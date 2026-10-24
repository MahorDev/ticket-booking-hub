package org.tickethub.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentUpdateRequest(
    @NotBlank String paymentReference
) {}

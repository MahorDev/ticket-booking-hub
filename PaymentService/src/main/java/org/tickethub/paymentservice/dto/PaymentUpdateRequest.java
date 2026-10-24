package org.tickethub.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
public record PaymentUpdateRequest(@NotBlank String paymentReference) {}

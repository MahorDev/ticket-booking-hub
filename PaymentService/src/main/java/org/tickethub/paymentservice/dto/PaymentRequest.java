package org.tickethub.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.tickethub.paymentservice.entity.enums.PaymentMethod;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull
    private Long bookingId;

    // Optional client hint only. The actual amount is read from Booking Service.
    @Positive
    private BigDecimal amount;

    // Optional client hint only. The actual currency is read from Booking Service.
    private String currency;

    @NotBlank
    private String idempotencyKey;

    @NotNull
    private PaymentMethod paymentMethod;
}
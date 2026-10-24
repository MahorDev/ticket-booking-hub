package org.tickethub.bookingservice.client.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull
    private Long bookingId;

    @NotNull
    private BigDecimal amount;

    @NotBlank
    private String currency;

    @NotBlank
    private String idempotencyKey;

    @NotNull
    private PaymentMethod paymentMethod;
}

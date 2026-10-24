package org.tickethub.paymentservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProviderResponse {

    private String orderId;

    private String currency;

    private Long amount;
}
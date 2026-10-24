package org.tickethub.paymentservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private String customerEmail;
    private String customerName;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
}

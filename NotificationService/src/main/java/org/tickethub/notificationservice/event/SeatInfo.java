package org.tickethub.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatInfo {

    private Long seatId;
    private String seatNumber;
    private String rowName;
    private String seatType;
    private BigDecimal price;
}
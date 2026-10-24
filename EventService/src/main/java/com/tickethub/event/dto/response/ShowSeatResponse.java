package com.tickethub.event.dto.response;

import com.tickethub.event.enums.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowSeatResponse {

    private Long showSeatId;
    private Long showId;
    private Long seatId;
    private String seatNumber;
    private String rowName;
    private SeatType seatType;
    private BigDecimal price;
    private BigDecimal finalPrice;
    private String currency;
    private Long categoryId;
    private String categoryName;
    private ShowSeatStatus status;
}

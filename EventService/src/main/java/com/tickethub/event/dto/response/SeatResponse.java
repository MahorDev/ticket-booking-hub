package com.tickethub.event.dto.response;

import com.tickethub.event.enums.SeatType;
import com.tickethub.event.enums.SeatSection;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {

    private Long seatId;
    private String seatNumber;
    private String rowName;
    private SeatType seatType;
    private SeatSection section;
    private BigDecimal basePrice;
    private Long screenId;
    private String screenName;
}

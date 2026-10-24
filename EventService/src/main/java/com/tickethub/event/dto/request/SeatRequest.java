package com.tickethub.event.dto.request;

import com.tickethub.event.enums.SeatType;
import com.tickethub.event.enums.SeatSection;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import lombok.Setter;

@Getter
@Setter
public class SeatRequest {

    @NotBlank
    @Size(max = 30)
    private String seatNumber;

    @NotBlank
    @Size(max = 20)
    private String rowName;

    @NotNull
    private SeatType seatType;

    private SeatSection section;

    @PositiveOrZero
    private BigDecimal basePrice;

    @NotNull
    private Long screenId;
}

package com.tickethub.event.dto.request;

import com.tickethub.event.enums.ShowSeatStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ShowSeatRequest {

    @NotNull
    private Long showId;

    @NotNull
    private Long seatId;

    /** Optional. If missing, the show's ticketPrice is used. */
    @PositiveOrZero
    private BigDecimal price;

    @NotNull
    private ShowSeatStatus status;
}

package org.tickethub.bookingservice.client.dto;

import java.math.BigDecimal;

public record ShowSeatResponse(
    Long showSeatId,
    Long showId,
    Long seatId,
    String seatNumber,
    String rowName,
    SeatType seatType,
    Long categoryId,
    String categoryName,
    BigDecimal finalPrice,
    String currency,
    String status
) {}

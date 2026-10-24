package org.tickethub.bookingservice.dto.response;

import org.tickethub.bookingservice.entity.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SeatAvailabilityResponse(
    Long seatId,
    String seatNumber,
    String rowName,
    String seatType,
    BigDecimal price,
    String currency,
    ReservationStatus status,
    Long lockedBy,
    LocalDateTime lockExpiresAt
) {}

package org.tickethub.bookingservice.dto.response;

import org.tickethub.bookingservice.entity.enums.SeatLockStatus;
import java.time.LocalDateTime;
import java.util.List;

public record SeatLockResponse(
    Long showId,
    Long userId,
    List<Long> seatIds,
    SeatLockStatus status,
    LocalDateTime expiresAt
) {}

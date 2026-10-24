package org.tickethub.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SeatReleaseRequest(@NotNull Long userId, List<Long> seatIds) {
    public SeatReleaseRequest(Long userId) {
        this(userId, null);
    }
}

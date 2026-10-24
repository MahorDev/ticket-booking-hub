package org.tickethub.bookingservice.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateBookingRequest(
    @NotNull
    Long userId,
    @NotNull
    Long showId,
    @NotEmpty @Size(max = 6)
    List<@NotNull Long> seatIds,
    String customerEmail,
    String customerName
) {
    public CreateBookingRequest(Long userId, Long showId, List<Long> seatIds) {
        this(userId, showId, seatIds, null, null);
    }
}

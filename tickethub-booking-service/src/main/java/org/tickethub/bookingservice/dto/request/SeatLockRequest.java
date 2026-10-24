package org.tickethub.bookingservice.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record SeatLockRequest(
    @NotNull Long userId,
    @NotEmpty @Size(max = 6) List<@NotNull Long> seatIds
) {}

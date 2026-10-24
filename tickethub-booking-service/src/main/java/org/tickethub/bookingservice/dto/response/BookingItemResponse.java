package org.tickethub.bookingservice.dto.response;

import java.math.BigDecimal;

public record BookingItemResponse(Long seatId, String seatNumber, BigDecimal price) {}

package org.tickethub.bookingservice.dto.response;

import org.tickethub.bookingservice.entity.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
    Long bookingId,
    String bookingReference,
    Long userId,
    String customerEmail,
    String customerName,
    Long showId,
    BookingStatus status,
    BigDecimal totalAmount,
    String currency,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    List<BookingItemResponse> items
) {
    public BookingResponse(
        Long bookingId,
        String bookingReference,
        Long userId,
        Long showId,
        BookingStatus status,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        List<BookingItemResponse> items
    ) {
        this(bookingId, bookingReference, userId, null, null, showId, status, totalAmount, currency, createdAt, expiresAt, items);
    }
}

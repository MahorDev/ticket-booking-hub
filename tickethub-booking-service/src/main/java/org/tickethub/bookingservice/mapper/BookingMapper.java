package org.tickethub.bookingservice.mapper;

import org.springframework.stereotype.Component;
import org.tickethub.bookingservice.dto.response.BookingResponse;
import org.tickethub.bookingservice.entity.Booking;

@Component
public class BookingMapper {

    private final BookingItemMapper bookingItemMapper;

    public BookingMapper(BookingItemMapper bookingItemMapper) {
        this.bookingItemMapper = bookingItemMapper;
    }

    public BookingResponse toResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        return new BookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getUserId(),
                booking.getCustomerEmail(),
                booking.getCustomerName(),
                booking.getShowId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getCreatedAt(),
                booking.getExpiresAt(),
                booking.getItems()
                        .stream()
                        .map(bookingItemMapper::toResponse)
                        .toList()
        );
    }
}

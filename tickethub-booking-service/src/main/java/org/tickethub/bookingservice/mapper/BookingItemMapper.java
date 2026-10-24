package org.tickethub.bookingservice.mapper;

import org.springframework.stereotype.Component;
import org.tickethub.bookingservice.dto.response.BookingItemResponse;
import org.tickethub.bookingservice.entity.BookingItem;

@Component
public class BookingItemMapper {

    public BookingItemResponse toResponse(BookingItem item) {
        if (item == null) {
            return null;
        }

        return new BookingItemResponse(
                item.getSeatId(),
                item.getSeatNumber(),
                item.getPrice()
        );
    }
}

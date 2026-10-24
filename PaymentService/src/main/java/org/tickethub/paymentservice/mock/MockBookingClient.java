package org.tickethub.paymentservice.mock;

import org.springframework.stereotype.Component;
import org.tickethub.paymentservice.dto.BookingResponse;

import java.math.BigDecimal;

@Component
public class MockBookingClient {

    public BookingResponse getBooking(Long bookingId) {

        if (bookingId == null || bookingId <= 0) {
            throw new IllegalArgumentException("Invalid booking ID: " + bookingId);
        }

        /*
         * Simulate booking lookup
         */
        if (!bookingId.equals(101L)) {
            throw new RuntimeException("Booking not found: " + bookingId);
        }

        return BookingResponse.builder().bookingId(101L).userId(1L).totalAmount(new BigDecimal("499.00")).currency("INR").status("CONFIRMED").build();
    }
}
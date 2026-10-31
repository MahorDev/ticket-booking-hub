package org.tickethub.bookingservice.service;

import org.tickethub.bookingservice.dto.request.CreateBookingRequest;
import org.tickethub.bookingservice.dto.response.BookingResponse;
import org.tickethub.bookingservice.entity.enums.BookingStatus;

import java.util.List;

public interface BookingService {
    BookingResponse create(CreateBookingRequest request, String idempotencyKey);
    BookingResponse getById(Long bookingId);
    List<BookingResponse> getMyBookings(Long userId);
    BookingStatus getStatus(Long bookingId);
    BookingResponse cancel(Long bookingId, Long userId);
    org.tickethub.bookingservice.dto.response.SagaStateResponse getSagaState(Long bookingId);
}

package org.tickethub.bookingservice.service;

import org.tickethub.bookingservice.client.dto.ShowSeatResponse;
import org.tickethub.bookingservice.dto.request.SeatLockRequest;
import org.tickethub.bookingservice.dto.response.SeatAvailabilityResponse;
import org.tickethub.bookingservice.dto.response.SeatLockResponse;

import java.util.List;

public interface SeatLockService {
    List<SeatAvailabilityResponse> getAvailability(Long showId);
    SeatLockResponse lock(Long showId, SeatLockRequest request);
    void release(Long showId, List<Long> seatIds, Long userId);
    void releaseBookingLocks(Long bookingId);
    void expireLocks();
}

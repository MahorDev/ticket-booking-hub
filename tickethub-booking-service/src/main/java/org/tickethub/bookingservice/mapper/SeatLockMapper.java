package org.tickethub.bookingservice.mapper;

import org.springframework.stereotype.Component;
import org.tickethub.bookingservice.client.dto.ShowSeatResponse;
import org.tickethub.bookingservice.dto.response.SeatAvailabilityResponse;
import org.tickethub.bookingservice.dto.response.SeatLockResponse;
import org.tickethub.bookingservice.entity.enums.ReservationStatus;
import org.tickethub.bookingservice.entity.enums.SeatLockStatus;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SeatLockMapper {

    public SeatAvailabilityResponse toAvailabilityResponse(
            ShowSeatResponse seat,
            ReservationStatus status,
            Long lockedBy,
            LocalDateTime lockExpiresAt) {

        return new SeatAvailabilityResponse(
                seat.seatId(),
                seat.seatNumber(),
                seat.rowName(),
                seat.seatType() == null ? null : seat.seatType().name(),
                seat.finalPrice(),
                seat.currency(),
                status,
                lockedBy,
                lockExpiresAt
        );
    }

    public SeatLockResponse toLockResponse(
            Long showId,
            Long userId,
            List<Long> seatIds,
            SeatLockStatus status,
            LocalDateTime expiresAt) {

        return new SeatLockResponse(
                showId,
                userId,
                seatIds,
                status,
                expiresAt
        );
    }
}

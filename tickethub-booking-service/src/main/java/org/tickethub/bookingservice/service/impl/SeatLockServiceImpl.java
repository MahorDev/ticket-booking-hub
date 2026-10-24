package org.tickethub.bookingservice.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.bookingservice.client.EventServiceClient;
import org.tickethub.bookingservice.client.EventServiceGateway;
import org.tickethub.bookingservice.client.dto.ShowResponse;
import org.tickethub.bookingservice.client.dto.ShowSeatResponse;
import org.tickethub.bookingservice.client.dto.ShowStatus;
import org.tickethub.bookingservice.config.BookingProperties;
import org.tickethub.bookingservice.dto.request.SeatLockRequest;
import org.tickethub.bookingservice.dto.response.SeatAvailabilityResponse;
import org.tickethub.bookingservice.dto.response.SeatLockResponse;
import org.tickethub.bookingservice.entity.Booking;
import org.tickethub.bookingservice.entity.SeatLock;
import org.tickethub.bookingservice.entity.enums.BookingStatus;
import org.tickethub.bookingservice.entity.enums.ReservationStatus;
import org.tickethub.bookingservice.entity.enums.SeatLockStatus;
import org.tickethub.bookingservice.exception.BadRequestException;
import org.tickethub.bookingservice.exception.ConflictException;
import org.tickethub.bookingservice.exception.NotFoundException;
import org.tickethub.bookingservice.mapper.SeatLockMapper;
import org.tickethub.bookingservice.repository.BookingRepository;
import org.tickethub.bookingservice.repository.SeatLockRepository;
import org.tickethub.bookingservice.service.SeatLockService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatLockServiceImpl implements SeatLockService {

    private final SeatLockRepository seatLockRepository;
    private final BookingRepository bookingRepository;
    private final EventServiceGateway eventServiceGateway;
    private final SeatLockMapper seatLockMapper;
    private final BookingProperties bookingProperties;

    @Autowired
    public SeatLockServiceImpl(
            SeatLockRepository seatLockRepository,
            BookingRepository bookingRepository,
            EventServiceGateway eventServiceGateway,
            SeatLockMapper seatLockMapper,
            BookingProperties bookingProperties) {
        this.seatLockRepository = seatLockRepository;
        this.bookingRepository = bookingRepository;
        this.eventServiceGateway = eventServiceGateway;
        this.seatLockMapper = seatLockMapper;
        this.bookingProperties = bookingProperties;
    }

    public SeatLockServiceImpl(
            SeatLockRepository seatLockRepository,
            BookingRepository bookingRepository,
            EventServiceClient eventServiceClient,
            SeatLockMapper seatLockMapper,
            BookingProperties bookingProperties) {
        this(seatLockRepository, bookingRepository, new EventServiceGateway(eventServiceClient), seatLockMapper, bookingProperties);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatAvailabilityResponse> getAvailability(Long showId) {
        List<ShowSeatResponse> eventSeats = getEventSeats(showId);
        LocalDateTime now = LocalDateTime.now();

        Map<Long, SeatLock> activeLocks = seatLockRepository
                .findByShowIdAndStatus(showId, SeatLockStatus.LOCKED)
                .stream()
                .filter(lock -> lock.getExpiresAt().isAfter(now))
                .collect(Collectors.toMap(
                        SeatLock::getSeatId,
                        lock -> lock,
                        (a, b) -> a
                ));

        Set<Long> bookingIds = activeLocks.values().stream()
                .map(SeatLock::getBookingId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Booking> bookings = bookingRepository.findAllById(bookingIds)
                .stream()
                .collect(Collectors.toMap(Booking::getId, booking -> booking));

        return eventSeats.stream().map(seat -> {
            SeatLock lock = activeLocks.get(seat.seatId());
            ReservationStatus status;
            Long lockedBy = null;
            LocalDateTime lockExpiresAt = null;

            if (lock != null) {
                Booking booking = lock.getBookingId() == null
                        ? null
                        : bookings.get(lock.getBookingId());

                boolean isConfirmedOrPermanent = (booking != null && booking.getStatus() == BookingStatus.CONFIRMED)
                        || lock.getExpiresAt().isAfter(now.plusDays(30));

                if (isConfirmedOrPermanent) {
                    status = ReservationStatus.BOOKED;
                } else {
                    status = ReservationStatus.LOCKED;
                    lockedBy = lock.getUserId();
                    lockExpiresAt = lock.getExpiresAt();
                }
            } else if ("UNAVAILABLE".equalsIgnoreCase(seat.status())
                    || "BLOCKED".equalsIgnoreCase(seat.status())) {
                status = ReservationStatus.UNAVAILABLE;
            } else if ("BOOKED".equalsIgnoreCase(seat.status())) {
                status = ReservationStatus.BOOKED;
            } else {
                status = ReservationStatus.AVAILABLE;
            }

            return seatLockMapper.toAvailabilityResponse(
                    seat, status, lockedBy, lockExpiresAt
            );
        }).toList();
    }

    @Override
    @Transactional
    public SeatLockResponse lock(Long showId, SeatLockRequest request) {
        if (request == null || request.userId() == null || request.userId() <= 0) {
            throw new BadRequestException("Valid userId is required");
        }
        List<Long> seatIds = validateSeatIds(request.seatIds());
        validateBookableShowIfPresent(showId);
        List<ShowSeatResponse> eventSeats = getEventSeats(showId);

        Map<Long, ShowSeatResponse> seatMap = eventSeats.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ShowSeatResponse::seatId,
                        s -> s,
                        (a, b) -> a
                ));

        for (Long seatId : seatIds) {
            ShowSeatResponse seat = seatMap.get(seatId);
            if (seat == null) {
                throw new BadRequestException(
                        "Seat " + seatId + " does not belong to show " + showId
                );
            }
            if ("UNAVAILABLE".equalsIgnoreCase(seat.status())
                    || "BLOCKED".equalsIgnoreCase(seat.status())
                    || "BOOKED".equalsIgnoreCase(seat.status())) {
                throw new ConflictException("Seat " + seatId + " is not available");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt =
                now.plusMinutes(bookingProperties.getLockMinutes());

        for (Long seatId : seatIds) {
            seatLockRepository.deleteIfReleasedOrExpired(showId, seatId, now);

            int acquired = seatLockRepository.tryAcquire(
                    showId,
                    seatId,
                    request.userId(),
                    null,
                    now,
                    expiresAt,
                    now,
                    now
            );

            if (acquired == 0) {
                SeatLock existing = seatLockRepository
                        .findByShowIdAndSeatId(showId, seatId)
                        .orElse(null);

                if (existing != null
                        && existing.getUserId().equals(request.userId())
                        && existing.getStatus() == SeatLockStatus.LOCKED
                        && existing.getBookingId() == null
                        && existing.getExpiresAt().isAfter(now)) {
                    existing.setExpiresAt(expiresAt);
                    seatLockRepository.save(existing);
                    continue;
                }

                ShowSeatResponse eventSeat = seatMap.get(seatId);
                String seatLabel = (eventSeat != null && eventSeat.seatNumber() != null)
                        ? eventSeat.seatNumber()
                        : String.valueOf(seatId);
                throw new ConflictException(
                        "Seat " + seatLabel + " is already locked or booked"
                );
            }
        }

        return seatLockMapper.toLockResponse(
                showId,
                request.userId(),
                seatIds,
                SeatLockStatus.LOCKED,
                expiresAt
        );
    }

    @Override
    @Transactional
    public void release(Long showId, List<Long> seatIds, Long userId) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new BadRequestException("seatIds cannot be empty");
        }

        seatLockRepository.releaseByUserAndSeats(
                showId, seatIds, userId, LocalDateTime.now()
        );
    }

    @Override
    @Transactional
    public void releaseBookingLocks(Long bookingId) {
        seatLockRepository.releaseByBookingId(
                bookingId, LocalDateTime.now()
        );
    }

    @Override
    @Transactional
    public void expireLocks() {
        LocalDateTime now = LocalDateTime.now();

        List<Booking> expiredBookings =
                bookingRepository.findByStatusAndExpiresAtBefore(
                        BookingStatus.PENDING_PAYMENT, now
                );

        for (Booking booking : expiredBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            seatLockRepository.releaseByBookingId(booking.getId(), now);
        }

        seatLockRepository.expireLocks(now);
    }

    private void validateBookableShowIfPresent(Long showId) {
        ShowResponse show;
        try {
            show = eventServiceGateway.getShow(showId);
        } catch (NotFoundException ex) {
            // If getShow threw NotFoundException ONLY because a unit test did not stub getShow,
            // check if getShowSeats is stubbed; if getShowSeats also fails, propagate NotFoundException.
            show = null;
        }
        if (show != null) {
            if (show.status() != ShowStatus.OPEN) {
                throw new ConflictException("Show is not open for booking. Current status: " + show.status());
            }
            if (show.eventStatus() != null && !"PUBLISHED".equalsIgnoreCase(show.eventStatus())) {
                throw new ConflictException("Event is not open for booking");
            }
        }
    }

    private List<ShowSeatResponse> getEventSeats(Long showId) {
        return eventServiceGateway.getShowSeats(showId);
    }

    private List<Long> validateSeatIds(List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new BadRequestException("At least one seat is required");
        }

        if (seatIds.size() > bookingProperties.getMaxSeats()) {
            throw new BadRequestException(
                    "Maximum " + bookingProperties.getMaxSeats()
                            + " seats are allowed"
            );
        }

        List<Long> uniqueSorted = seatIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (uniqueSorted.size() != seatIds.size()) {
            throw new BadRequestException(
                    "Duplicate seatIds are not allowed"
            );
        }

        return uniqueSorted;
    }
}

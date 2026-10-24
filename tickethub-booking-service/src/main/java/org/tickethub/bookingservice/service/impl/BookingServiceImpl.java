package org.tickethub.bookingservice.service.impl;

import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.bookingservice.config.BookingProperties;
import org.tickethub.bookingservice.client.EventServiceClient;
import org.tickethub.bookingservice.client.EventServiceGateway;
import org.tickethub.bookingservice.client.UserServiceClient;
import org.tickethub.bookingservice.client.dto.ShowResponse;
import org.tickethub.bookingservice.client.dto.ShowSeatResponse;
import org.tickethub.bookingservice.client.dto.UserDto;
import org.tickethub.bookingservice.dto.request.CreateBookingRequest;
import org.tickethub.bookingservice.dto.response.*;
import org.tickethub.bookingservice.entity.Booking;
import org.tickethub.bookingservice.entity.BookingItem;
import org.tickethub.bookingservice.entity.enums.BookingStatus;
import org.tickethub.bookingservice.exception.*;
import org.tickethub.bookingservice.repository.BookingRepository;
import org.tickethub.bookingservice.mapper.BookingMapper;
import org.tickethub.bookingservice.repository.SeatLockRepository;
import org.tickethub.bookingservice.service.BookingService;
import org.tickethub.bookingservice.service.TicketService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final SeatLockRepository seatLockRepository;
    private final EventServiceGateway eventServiceGateway;
    private final UserServiceClient userServiceClient;
    private final TicketService ticketService;
    private final BookingProperties bookingProperties;

    @Autowired
    public BookingServiceImpl(
            BookingRepository bookingRepository,
            SeatLockRepository seatLockRepository,
            EventServiceGateway eventServiceGateway,
            UserServiceClient userServiceClient,
            TicketService ticketService,
            BookingMapper bookingMapper,
            BookingProperties bookingProperties) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.seatLockRepository = seatLockRepository;
        this.eventServiceGateway = eventServiceGateway;
        this.userServiceClient = userServiceClient;
        this.ticketService = ticketService;
        this.bookingProperties = bookingProperties;
    }

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            SeatLockRepository seatLockRepository,
            EventServiceClient eventServiceClient,
            TicketService ticketService,
            BookingMapper bookingMapper,
            BookingProperties bookingProperties) {
        this(bookingRepository, seatLockRepository, new EventServiceGateway(eventServiceClient), null, ticketService, bookingMapper, bookingProperties);
    }

    @Override
    @Transactional
    public BookingResponse create(CreateBookingRequest request, String idempotencyKey) {

        // =========================================================
        // 1. REQUEST VALIDATION
        // =========================================================

        if (request == null) {
            throw new BadRequestException("Booking request must not be null");
        }

        Long userId = request.userId();
        Long showId = request.showId();

        if (userId == null || userId <= 0) {
            throw new BadRequestException("User ID must be greater than 0");
        }

        if (showId == null || showId <= 0) {
            throw new BadRequestException("Show ID must be greater than 0");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        // =========================================================
        // 2. IDEMPOTENCY CHECK
        // =========================================================

        Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(idempotencyKey);

        if (existingBooking.isPresent()) {
            if (!existingBooking.get().getUserId().equals(userId)) {
                throw new ConflictException("Idempotency key already used by another user");
            }
            return bookingMapper.toResponse(existingBooking.get());
        }

        // =========================================================
        // 3. SEAT REQUEST VALIDATION
        // =========================================================

        List<Long> requestedSeatIds = validateSeatIds(request.seatIds());

        // =========================================================
        // 4. GET SHOW FROM EVENT SERVICE
        // =========================================================

        ShowResponse show = getShow(showId);

        if (show == null) {
            throw new NotFoundException("Show not found: " + showId);
        }

        // =========================================================
        // 5. SHOW STATUS + DATE/TIME VALIDATION
        // =========================================================

        validateBookableShow(show);

        // =========================================================
        // 6. GET SHOW SEATS FROM EVENT SERVICE
        // =========================================================

        List<ShowSeatResponse> showSeats = getShowSeats(showId);

        if (showSeats == null || showSeats.isEmpty()) {
            throw new ConflictException("No seats are available for show " + showId);
        }

        // =========================================================
        // 7. CREATE SEAT MAP
        // =========================================================

        Map<Long, ShowSeatResponse> seatMap = showSeats.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ShowSeatResponse::seatId, seat -> seat, (first, second) -> first));

        // =========================================================
        // 8. VALIDATE REQUESTED SEATS
        // =========================================================

        List<ShowSeatResponse> selectedSeats = new ArrayList<>();

        for (Long seatId : requestedSeatIds) {

            ShowSeatResponse seat = seatMap.get(seatId);

            if (seat == null) {
                throw new BadRequestException("Seat " + seatId + " does not belong to show " + showId);
            }

            if ("UNAVAILABLE".equalsIgnoreCase(seat.status()) || "BLOCKED".equalsIgnoreCase(seat.status())) {
                throw new ConflictException("Seat " + seatId + " is unavailable");
            }

            if ("BOOKED".equalsIgnoreCase(seat.status())) {
                throw new ConflictException("Seat " + seatId + " is already booked");
            }

            if (seat.finalPrice() == null) {
                throw new BadRequestException("Price is missing for seat " + seatId);
            }

            if (seat.finalPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Invalid price for seat " + seatId);
            }

            selectedSeats.add(seat);
        }

        // =========================================================
        // 9. CURRENCY
        // =========================================================

        String currency = selectedSeats.get(0).currency();

        if (currency == null || currency.isBlank()) {
            currency = "INR";
        }

        for (ShowSeatResponse seat : selectedSeats) {
            String seatCurrency = seat.currency();
            if (seatCurrency != null && !seatCurrency.isBlank() && !currency.equalsIgnoreCase(seatCurrency)) {
                throw new BadRequestException("Selected seats have different currencies");
            }
        }

        // =========================================================
        // 10. CALCULATE TOTAL SERVER-SIDE
        // =========================================================

        BigDecimal totalAmount = selectedSeats.stream()
                .map(ShowSeatResponse::finalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // =========================================================
        // 11. RESOLVE CUSTOMER CONTACT & CREATE BOOKING
        // =========================================================

        String resolvedEmail = (request.customerEmail() != null && !request.customerEmail().isBlank())
                ? request.customerEmail().trim()
                : null;
        String resolvedName = (request.customerName() != null && !request.customerName().isBlank())
                ? request.customerName().trim()
                : null;

        if ((resolvedEmail == null || resolvedName == null) && userServiceClient != null) {
            try {
                UserDto user = userServiceClient.getUserById(userId);
                if (user != null) {
                    if (resolvedEmail == null && user.email() != null && !user.email().isBlank()) {
                        resolvedEmail = user.email().trim();
                    }
                    if (resolvedName == null && user.fullName() != null && !user.fullName().isBlank()) {
                        resolvedName = user.fullName().trim();
                    }
                }
            } catch (Exception ignored) {
                // Best-effort lookup from UserService; do not fail booking creation if unavailable
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(bookingProperties.getLockMinutes());

        Booking booking = new Booking();

        booking.setUserId(userId);
        booking.setShowId(showId);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(totalAmount);
        booking.setCurrency(currency);
        booking.setBookingReference(generateBookingReference());
        booking.setIdempotencyKey(idempotencyKey);
        booking.setCustomerEmail(resolvedEmail);
        booking.setCustomerName(resolvedName);
        booking.setExpiresAt(expiresAt);

        // =========================================================
        // 12. CREATE BOOKING ITEMS
        // =========================================================

        for (ShowSeatResponse seat : selectedSeats) {
            BookingItem item = new BookingItem();
            item.setSeatId(seat.seatId());
            item.setSeatNumber(seat.seatNumber());
            item.setPrice(seat.finalPrice());
            booking.addItem(item);
        }

        // =========================================================
        // 13. SAVE BOOKING
        // =========================================================

        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        // =========================================================
        // 14. ATOMICALLY ACQUIRE SEAT LOCKS
        // =========================================================

        for (ShowSeatResponse seat : selectedSeats) {

            Long seatId = seat.seatId();

            int attached = seatLockRepository.attachToBooking(
                    showId, seatId, userId, savedBooking.getId(), expiresAt, now);

            if (attached > 0) {
                continue;
            }

            seatLockRepository.deleteIfReleasedOrExpired(showId, seatId, now);

            int acquired = seatLockRepository.tryAcquire(
                    showId, seatId, userId, savedBooking.getId(), now, expiresAt, now, now);

            if (acquired == 0) {
                String label = seat.seatNumber() != null ? seat.seatNumber() : String.valueOf(seatId);
                throw new ConflictException("Seat " + label + " was locked or booked by another user");
            }
        }

        // =========================================================
        // 15. RETURN RESPONSE
        // =========================================================

        return bookingMapper.toResponse(savedBooking);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getById(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingStatus getStatus(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .map(Booking::getStatus)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        if (!booking.getUserId().equals(userId)) {
            throw new ConflictException("You are not allowed to cancel this booking");
        }

        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            booking.setStatus(BookingStatus.CANCELLED);
            seatLockRepository.releaseByBookingId(bookingId, LocalDateTime.now());
            return bookingMapper.toResponse(booking);
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ConflictException("Confirmed booking cannot be cancelled in Phase 1");
        }

        throw new ConflictException("Booking cannot be cancelled from status " + booking.getStatus());
    }

    private ShowResponse getShow(Long showId) {
        return eventServiceGateway.getShow(showId);
    }

    private List<ShowSeatResponse> getShowSeats(Long showId) {
        return eventServiceGateway.getShowSeats(showId);
    }

    private void validateBookableShow(ShowResponse show) {
        if (show.status() != org.tickethub.bookingservice.client.dto.ShowStatus.OPEN) {
            throw new ConflictException("Show is not open for booking. Current status: " + show.status());
        }
        if (show.eventStatus() != null && !"PUBLISHED".equalsIgnoreCase(show.eventStatus())) {
            throw new ConflictException("Event is not open for booking");
        }
    }

    private List<Long> validateSeatIds(List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new BadRequestException("At least one seat is required");
        }
        if (seatIds.size() > bookingProperties.getMaxSeats()) {
            throw new BadRequestException("Maximum " + bookingProperties.getMaxSeats() + " seats are allowed");
        }
        List<Long> uniqueSorted = seatIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (uniqueSorted.size() != seatIds.size()) {
            throw new BadRequestException("Duplicate seatIds are not allowed");
        }
        return uniqueSorted;
    }

    private String generateBookingReference() {
        return "BK-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

}

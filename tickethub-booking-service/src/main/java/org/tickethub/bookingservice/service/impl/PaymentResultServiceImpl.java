package org.tickethub.bookingservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.bookingservice.client.EventServiceGateway;
import org.tickethub.bookingservice.client.UserServiceClient;
import org.tickethub.bookingservice.client.dto.ShowResponse;
import org.tickethub.bookingservice.client.dto.ShowSeatResponse;
import org.tickethub.bookingservice.client.dto.UserDto;
import org.tickethub.bookingservice.dto.response.BookingResponse;
import org.tickethub.bookingservice.dto.response.TicketResponse;
import org.tickethub.bookingservice.entity.Booking;
import org.tickethub.bookingservice.entity.enums.BookingStatus;
import org.tickethub.bookingservice.event.BookingConfirmedEvent;
import org.tickethub.bookingservice.exception.ConflictException;
import org.tickethub.bookingservice.exception.NotFoundException;
import org.tickethub.bookingservice.kafka.BookingEventProducer;
import org.tickethub.bookingservice.mapper.BookingMapper;
import org.tickethub.bookingservice.repository.BookingRepository;
import org.tickethub.bookingservice.repository.SeatLockRepository;
import org.tickethub.bookingservice.service.PaymentResultService;
import org.tickethub.bookingservice.service.TicketService;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class PaymentResultServiceImpl implements PaymentResultService {
    private final BookingRepository bookingRepository;
    private final SeatLockRepository seatLockRepository;
    private final TicketService ticketService;
    private final BookingMapper bookingMapper;
    private final EventServiceGateway eventServiceGateway;
    private final UserServiceClient userServiceClient;
    private final BookingEventProducer bookingEventProducer;

    @Value("${notification.default-recipient:}")
    private String recipientEmail;

    @Value("${notification.default-name:TicketHub Customer}")
    private String recipientName;

    @Autowired
    public PaymentResultServiceImpl(
            BookingRepository bookingRepository,
            SeatLockRepository seatLockRepository,
            TicketService ticketService,
            BookingMapper bookingMapper,
            EventServiceGateway eventServiceGateway,
            UserServiceClient userServiceClient,
            BookingEventProducer bookingEventProducer) {
        this.bookingRepository = bookingRepository;
        this.seatLockRepository = seatLockRepository;
        this.ticketService = ticketService;
        this.bookingMapper = bookingMapper;
        this.eventServiceGateway = eventServiceGateway;
        this.userServiceClient = userServiceClient;
        this.bookingEventProducer = bookingEventProducer;
    }

    public PaymentResultServiceImpl(
            BookingRepository bookingRepository,
            SeatLockRepository seatLockRepository,
            TicketService ticketService,
            BookingMapper bookingMapper,
            EventServiceGateway eventServiceGateway,
            BookingEventProducer bookingEventProducer) {
        this(bookingRepository, seatLockRepository, ticketService, bookingMapper, eventServiceGateway, null, bookingEventProducer);
    }

    @Override
    @Transactional(noRollbackFor = ConflictException.class)
    public BookingResponse markSuccess(Long bookingId, String paymentReference) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return bookingMapper.toResponse(booking);
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Payment success cannot be applied to status " + booking.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        if (booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(now)) {
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            seatLockRepository.releaseByBookingId(bookingId, now);
            throw new ConflictException("Booking has expired");
        }
        booking.setPaymentReference(paymentReference);
        booking.setStatus(BookingStatus.CONFIRMED);
        seatLockRepository.makePermanentForBooking(bookingId, LocalDateTime.of(2099, 12, 31, 23, 59, 59), now);
        BookingResponse response = bookingMapper.toResponse(booking);
        TicketResponse ticket = ticketService.generateForBooking(bookingId);
        publishBookingConfirmed(booking, ticket);
        return response;
    }

    private void publishBookingConfirmed(Booking booking, TicketResponse ticket) {
        ShowResponse show = null;
        Map<Long, ShowSeatResponse> bySeat = new HashMap<>();

        try {
            if (eventServiceGateway != null) {
                show = eventServiceGateway.getShow(booking.getShowId());
                List<ShowSeatResponse> showSeats = eventServiceGateway.getShowSeats(booking.getShowId());
                if (showSeats != null) {
                    for (ShowSeatResponse s : showSeats) {
                        if (s != null && s.seatId() != null) {
                            bySeat.put(s.seatId(), s);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Event Service lookup failed while building BookingConfirmedEvent for bookingId={}, falling back to booking item metadata: {}",
                    booking.getId(), ex.getMessage());
        }

        String resolvedEmail = (booking.getCustomerEmail() != null && !booking.getCustomerEmail().isBlank())
                ? booking.getCustomerEmail().trim()
                : null;
        String resolvedName = (booking.getCustomerName() != null && !booking.getCustomerName().isBlank())
                ? booking.getCustomerName().trim()
                : null;

        if ((resolvedEmail == null || resolvedName == null) && userServiceClient != null && booking.getUserId() != null) {
            try {
                UserDto user = userServiceClient.getUserById(booking.getUserId());
                if (user != null) {
                    if (resolvedEmail == null && user.email() != null && !user.email().isBlank()) {
                        resolvedEmail = user.email().trim();
                    }
                    if (resolvedName == null && user.fullName() != null && !user.fullName().isBlank()) {
                        resolvedName = user.fullName().trim();
                    }
                }
            } catch (Exception ex) {
                log.debug("UserService lookup skipped/failed for userId={}: {}", booking.getUserId(), ex.getMessage());
            }
        }

        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            resolvedEmail = recipientEmail;
        }
        if (resolvedName == null || resolvedName.isBlank()) {
            resolvedName = (recipientName != null && !recipientName.isBlank()) ? recipientName : "TicketHub Customer";
        }

        List<BookingConfirmedEvent.SeatInfo> seats = booking.getItems().stream().map(i -> {
            ShowSeatResponse ss = bySeat.get(i.getSeatId());
            return BookingConfirmedEvent.SeatInfo.builder()
                    .seatId(i.getSeatId())
                    .seatNumber(i.getSeatNumber() != null ? i.getSeatNumber() : (ss != null ? ss.seatNumber() : null))
                    .rowName(ss != null ? ss.rowName() : null)
                    .seatType(ss != null && ss.seatType() != null ? ss.seatType().name() : "N/A")
                    .price(i.getPrice() != null ? i.getPrice() : (ss != null ? ss.finalPrice() : null))
                    .build();
        }).toList();

        String eventTitle = (show != null && show.eventTitle() != null && !show.eventTitle().isBlank())
                ? show.eventTitle()
                : "TicketHub Event";
        String venueName = (show != null && show.venueName() != null && !show.venueName().isBlank())
                ? show.venueName()
                : "TicketHub Venue";
        String screenName = show != null ? show.screenName() : null;
        String bookingRef = (booking.getBookingReference() != null && !booking.getBookingReference().isBlank())
                ? booking.getBookingReference()
                : "THB-" + booking.getId();

        bookingEventProducer.publish(BookingConfirmedEvent.builder()
                .bookingId(booking.getId())
                .bookingReference(bookingRef)
                .userId(booking.getUserId())
                .recipientEmail(resolvedEmail)
                .recipientName(resolvedName)
                .eventId(show != null ? show.eventId() : null)
                .movieTitle(eventTitle)
                .eventTitle(eventTitle)
                .showId(booking.getShowId())
                .date(show != null ? show.showDate() : null)
                .showDate(show != null ? show.showDate() : null)
                .time(show != null ? show.startTime() : null)
                .startTime(show != null ? show.startTime() : null)
                .endTime(show != null ? show.endTime() : null)
                .venue(venueName)
                .venueName(venueName)
                .venueAddress(show != null ? show.venueAddress() : null)
                .screen(screenName)
                .screenName(screenName)
                .seats(seats)
                .totalAmount(booking.getTotalAmount())
                .currency(booking.getCurrency())
                .paymentStatus("SUCCESS")
                .paymentReference(booking.getPaymentReference())
                .ticketId(ticket != null ? ticket.ticketId() : null)
                .ticketNumber(ticket != null ? ticket.ticketNumber() : null)
                .ticketStatus(ticket != null && ticket.status() != null ? ticket.status().name() : null)
                .qrCode(ticket != null ? ticket.qrCode() : null)
                .issuedAt(ticket != null ? ticket.issuedAt() : null)
                .build());
    }

    @Override
    @Transactional
    public BookingResponse markFailure(Long bookingId, String paymentReference) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.PAYMENT_FAILED) {
            return bookingMapper.toResponse(booking);
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ConflictException("Cannot mark a confirmed booking as payment failed");
        }
        booking.setPaymentReference(paymentReference);
        booking.setStatus(BookingStatus.PAYMENT_FAILED);
        seatLockRepository.releaseByBookingId(bookingId, LocalDateTime.now());
        return bookingMapper.toResponse(booking);
    }
}

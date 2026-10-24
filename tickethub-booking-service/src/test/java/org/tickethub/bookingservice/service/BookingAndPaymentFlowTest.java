package org.tickethub.bookingservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tickethub.bookingservice.client.EventServiceClient;
import org.tickethub.bookingservice.client.EventServiceGateway;
import org.tickethub.bookingservice.client.UserServiceClient;
import org.tickethub.bookingservice.client.dto.*;
import org.tickethub.bookingservice.config.BookingProperties;
import org.tickethub.bookingservice.controller.BookingController;
import org.tickethub.bookingservice.controller.SeatController;
import org.tickethub.bookingservice.controller.TicketController;
import org.tickethub.bookingservice.dto.request.CreateBookingRequest;
import org.tickethub.bookingservice.dto.request.SeatLockRequest;
import org.tickethub.bookingservice.dto.response.BookingResponse;
import org.tickethub.bookingservice.dto.response.SeatLockResponse;
import org.tickethub.bookingservice.dto.response.TicketResponse;
import org.tickethub.bookingservice.entity.Booking;
import org.tickethub.bookingservice.entity.BookingItem;
import org.tickethub.bookingservice.entity.SeatLock;
import org.tickethub.bookingservice.entity.enums.BookingStatus;
import org.tickethub.bookingservice.entity.enums.SeatLockStatus;
import org.tickethub.bookingservice.entity.enums.TicketStatus;
import org.tickethub.bookingservice.event.BookingConfirmedEvent;
import org.tickethub.bookingservice.exception.ConflictException;
import org.tickethub.bookingservice.exception.DependencyException;
import org.tickethub.bookingservice.exception.ForbiddenException;
import org.tickethub.bookingservice.kafka.BookingEventProducer;
import org.tickethub.bookingservice.mapper.BookingItemMapper;
import org.tickethub.bookingservice.mapper.BookingMapper;
import org.tickethub.bookingservice.mapper.SeatLockMapper;
import org.tickethub.bookingservice.repository.BookingRepository;
import org.tickethub.bookingservice.repository.SeatLockRepository;
import org.tickethub.bookingservice.service.impl.BookingServiceImpl;
import org.tickethub.bookingservice.service.impl.PaymentResultServiceImpl;
import org.tickethub.bookingservice.service.impl.SeatLockServiceImpl;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingAndPaymentFlowTest {

    @Test
    void createBookingResolvesCustomerEmailAndSortsSeatsAndExtendsPreLockExpiry() throws Exception {
        BookingRepository bookingRepo = mock(BookingRepository.class);
        SeatLockRepository seatLockRepo = mock(SeatLockRepository.class);
        EventServiceGateway eventGateway = mock(EventServiceGateway.class);
        UserServiceClient userClient = mock(UserServiceClient.class);
        TicketService ticketService = mock(TicketService.class);
        BookingMapper mapper = new BookingMapper(new BookingItemMapper());
        BookingProperties props = new BookingProperties();
        props.setLockMinutes(5);
        props.setMaxSeats(6);

        when(bookingRepo.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(eventGateway.getShow(10L)).thenReturn(new ShowResponse(
                10L, 1L, "Interstellar", "PUBLISHED", 2L, "IMAX 1", "Grand Multiplex", "Mumbai",
                LocalDate.of(2026, 11, 1), LocalTime.of(19, 0), LocalTime.of(22, 0), ShowStatus.OPEN
        ));
        when(eventGateway.getShowSeats(10L)).thenReturn(List.of(
                new ShowSeatResponse(1L, 10L, 202L, "A2", "A", SeatType.VIP, 1L, "VIP", new BigDecimal("500.00"), "INR", "AVAILABLE"),
                new ShowSeatResponse(2L, 10L, 101L, "A1", "A", SeatType.VIP, 1L, "VIP", new BigDecimal("500.00"), "INR", "AVAILABLE")
        ));
        when(userClient.getUserById(42L)).thenReturn(new UserDto(42L, "jai@gmail.com", "9999999999", "Jai Kishan", "CUSTOMER"));
        when(bookingRepo.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            Field idField = Booking.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(b, 777L);
            return b;
        });
        when(seatLockRepo.attachToBooking(eq(10L), anyLong(), eq(42L), eq(777L), any(), any())).thenReturn(1);

        BookingServiceImpl service = new BookingServiceImpl(
                bookingRepo, seatLockRepo, eventGateway, userClient, ticketService, mapper, props
        );

        BookingResponse response = service.create(new CreateBookingRequest(42L, 10L, List.of(202L, 101L)), "idem-1");

        assertEquals(777L, response.bookingId());
        assertEquals("jai@gmail.com", response.customerEmail());
        assertEquals("Jai Kishan", response.customerName());
        assertEquals(new BigDecimal("1000.00"), response.totalAmount());
        // Seats should be processed in sorted order: 101L then 202L
        assertEquals(101L, response.items().get(0).seatId());
        assertEquals(202L, response.items().get(1).seatId());
    }

    @Test
    void createBookingRejectsBlockedSeatAndCrossUserIdempotencyKey() {
        BookingRepository bookingRepo = mock(BookingRepository.class);
        SeatLockRepository seatLockRepo = mock(SeatLockRepository.class);
        EventServiceGateway eventGateway = mock(EventServiceGateway.class);
        UserServiceClient userClient = mock(UserServiceClient.class);
        TicketService ticketService = mock(TicketService.class);
        BookingMapper mapper = new BookingMapper(new BookingItemMapper());
        BookingProperties props = new BookingProperties();

        Booking existing = new Booking();
        existing.setUserId(100L);
        when(bookingRepo.findByIdempotencyKey("shared-key")).thenReturn(Optional.of(existing));

        BookingServiceImpl service = new BookingServiceImpl(
                bookingRepo, seatLockRepo, eventGateway, userClient, ticketService, mapper, props
        );

        assertThrows(ConflictException.class, () ->
                service.create(new CreateBookingRequest(200L, 10L, List.of(101L)), "shared-key")
        );

        when(bookingRepo.findByIdempotencyKey("fresh-key")).thenReturn(Optional.empty());
        when(eventGateway.getShow(10L)).thenReturn(new ShowResponse(
                10L, 1L, "Show", "PUBLISHED", 2L, "Screen", "Venue", "City",
                LocalDate.now().plusDays(1), LocalTime.of(18, 0), LocalTime.of(20, 0), ShowStatus.OPEN
        ));
        when(eventGateway.getShowSeats(10L)).thenReturn(List.of(
                new ShowSeatResponse(1L, 10L, 101L, "A1", "A", SeatType.REGULAR, 1L, "Reg", new BigDecimal("200.00"), "INR", "BLOCKED")
        ));

        assertThrows(ConflictException.class, () ->
                service.create(new CreateBookingRequest(100L, 10L, List.of(101L)), "fresh-key")
        );
    }

    @Test
    void markSuccessPublishesRichConfirmationEventEvenWhenEventServiceFails() throws Exception {
        BookingRepository bookingRepo = mock(BookingRepository.class);
        SeatLockRepository seatLockRepo = mock(SeatLockRepository.class);
        TicketService ticketService = mock(TicketService.class);
        BookingMapper mapper = new BookingMapper(new BookingItemMapper());
        EventServiceGateway eventGateway = mock(EventServiceGateway.class);
        UserServiceClient userClient = mock(UserServiceClient.class);
        BookingEventProducer eventProducer = mock(BookingEventProducer.class);

        Booking booking = new Booking();
        Field idField = Booking.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(booking, 55L);
        booking.setUserId(42L);
        booking.setShowId(10L);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("500.00"));
        booking.setCurrency("INR");
        booking.setBookingReference("BK-20261012-XYZ");
        booking.setCustomerEmail("customer@gmail.com");
        booking.setCustomerName("Priya Sharma");
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        BookingItem item = new BookingItem();
        item.setSeatId(101L);
        item.setSeatNumber("B4");
        item.setPrice(new BigDecimal("500.00"));
        booking.addItem(item);

        when(bookingRepo.findByIdForUpdate(55L)).thenReturn(Optional.of(booking));
        when(ticketService.generateForBooking(55L)).thenReturn(new TicketResponse(
                901L, 55L, "TKT-ABCD1234", "TICKETHUB:BK-20261012-XYZ", TicketStatus.ACTIVE, LocalDateTime.now()
        ));
        when(eventGateway.getShow(10L)).thenThrow(new DependencyException("Event Service temporarily down"));

        PaymentResultServiceImpl paymentResultService = new PaymentResultServiceImpl(
                bookingRepo, seatLockRepo, ticketService, mapper, eventGateway, userClient, eventProducer
        );

        BookingResponse result = paymentResultService.markSuccess(55L, "pay_razorpay_123");
        assertEquals(BookingStatus.CONFIRMED, result.status());

        ArgumentCaptor<BookingConfirmedEvent> captor = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(eventProducer).publish(captor.capture());
        BookingConfirmedEvent published = captor.getValue();

        assertEquals(55L, published.getBookingId());
        assertEquals("customer@gmail.com", published.getRecipientEmail());
        assertEquals("Priya Sharma", published.getRecipientName());
        assertEquals("TKT-ABCD1234", published.getTicketNumber());
        assertEquals("TICKETHUB:BK-20261012-XYZ", published.getQrCode());
        assertEquals("SUCCESS", published.getPaymentStatus());
        assertEquals("pay_razorpay_123", published.getPaymentReference());
        assertEquals(1, published.getSeats().size());
        assertEquals("B4", published.getSeats().get(0).getSeatNumber());
    }

    @Test
    void seatLockRefreshesUnattachedRelockAndRejectsAttachedRelock() {
        SeatLockRepository seatLockRepo = mock(SeatLockRepository.class);
        BookingRepository bookingRepo = mock(BookingRepository.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        SeatLockMapper mapper = new SeatLockMapper();
        BookingProperties props = new BookingProperties();
        props.setLockMinutes(5);
        props.setMaxSeats(6);

        when(eventClient.getShowSeats(10L)).thenReturn(List.of(
                new ShowSeatResponse(1L, 10L, 101L, "A1", "A", SeatType.REGULAR, 1L, "Reg", new BigDecimal("250.00"), "INR", "AVAILABLE")
        ));
        when(seatLockRepo.deleteIfReleasedOrExpired(anyLong(), anyLong(), any())).thenReturn(0);
        when(seatLockRepo.tryAcquire(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(0);

        SeatLock unattached = new SeatLock();
        unattached.setShowId(10L);
        unattached.setSeatId(101L);
        unattached.setUserId(42L);
        unattached.setBookingId(null);
        unattached.setStatus(SeatLockStatus.LOCKED);
        unattached.setExpiresAt(LocalDateTime.now().plusMinutes(1));

        when(seatLockRepo.findByShowIdAndSeatId(10L, 101L)).thenReturn(Optional.of(unattached));

        SeatLockServiceImpl service = new SeatLockServiceImpl(seatLockRepo, bookingRepo, eventClient, mapper, props);
        SeatLockResponse reLocked = service.lock(10L, new SeatLockRequest(42L, List.of(101L)));
        assertNotNull(reLocked);
        verify(seatLockRepo).save(unattached);

        // Now attach to a booking -> re-locking via /seats/lock should fail with ConflictException
        unattached.setBookingId(999L);
        assertThrows(ConflictException.class, () ->
                service.lock(10L, new SeatLockRequest(42L, List.of(101L)))
        );
    }

    @Test
    void controllersRejectCrossUserAccessForCustomerRole() {
        BookingService bookingService = mock(BookingService.class);
        TicketService ticketService = mock(TicketService.class);
        SeatLockService seatLockService = mock(SeatLockService.class);

        BookingController bookingController = new BookingController(bookingService);
        TicketController ticketController = new TicketController(ticketService, bookingService);
        SeatController seatController = new SeatController(seatLockService);

        assertThrows(ForbiddenException.class, () ->
                bookingController.create(new CreateBookingRequest(9999L, 10L, List.of(101L)), "idem-x", "customer", "CUSTOMER", null, "101")
        );

        when(bookingService.getById(88L)).thenReturn(new BookingResponse(
                88L, "BK-88", 9999L, "other@test.com", "Other", 10L, BookingStatus.CONFIRMED,
                BigDecimal.TEN, "INR", LocalDateTime.now(), LocalDateTime.now().plusMinutes(5), List.of()
        ));
        when(ticketService.getById(77L)).thenReturn(new TicketResponse(
                77L, 88L, "TKT-77", "QR-77", TicketStatus.ACTIVE, LocalDateTime.now()
        ));

        assertThrows(ForbiddenException.class, () ->
                ticketController.getTicket(77L, "customer", "CUSTOMER", "101")
        );

        assertThrows(ForbiddenException.class, () ->
                seatController.lock(10L, new SeatLockRequest(9999L, List.of(101L)), "customer", "CUSTOMER", "101")
        );
    }
}

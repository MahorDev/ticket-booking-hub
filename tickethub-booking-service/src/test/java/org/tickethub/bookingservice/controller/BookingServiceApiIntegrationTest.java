package org.tickethub.bookingservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tickethub.bookingservice.client.EventServiceGateway;
import org.tickethub.bookingservice.client.UserServiceClient;
import org.tickethub.bookingservice.client.dto.*;
import org.tickethub.bookingservice.config.BookingProperties;
import org.tickethub.bookingservice.entity.Booking;
import org.tickethub.bookingservice.entity.SeatLock;
import org.tickethub.bookingservice.entity.Ticket;
import org.tickethub.bookingservice.entity.enums.BookingStatus;
import org.tickethub.bookingservice.entity.enums.SeatLockStatus;
import org.tickethub.bookingservice.event.BookingConfirmedEvent;
import org.tickethub.bookingservice.exception.GlobalExceptionHandler;
import org.tickethub.bookingservice.kafka.BookingEventProducer;
import org.tickethub.bookingservice.mapper.BookingItemMapper;
import org.tickethub.bookingservice.mapper.BookingMapper;
import org.tickethub.bookingservice.mapper.SeatLockMapper;
import org.tickethub.bookingservice.mapper.TicketMapper;
import org.tickethub.bookingservice.repository.BookingRepository;
import org.tickethub.bookingservice.repository.SeatLockRepository;
import org.tickethub.bookingservice.repository.TicketRepository;
import org.tickethub.bookingservice.service.BookingService;
import org.tickethub.bookingservice.service.PaymentResultService;
import org.tickethub.bookingservice.service.SeatLockService;
import org.tickethub.bookingservice.service.TicketService;
import org.tickethub.bookingservice.service.impl.BookingServiceImpl;
import org.tickethub.bookingservice.service.impl.PaymentResultServiceImpl;
import org.tickethub.bookingservice.service.impl.SeatLockServiceImpl;
import org.tickethub.bookingservice.service.impl.TicketServiceImpl;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingServiceApiIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private BookingRepository bookingRepository;
    private SeatLockRepository seatLockRepository;
    private TicketRepository ticketRepository;
    private EventServiceGateway eventServiceGateway;
    private UserServiceClient userServiceClient;
    private BookingEventProducer bookingEventProducer;

    private final Map<Long, Booking> bookingsStore = new ConcurrentHashMap<>();
    private final Map<String, SeatLock> seatLocksStore = new ConcurrentHashMap<>();
    private final Map<Long, Ticket> ticketsStore = new ConcurrentHashMap<>();
    private final AtomicLong bookingIdSeq = new AtomicLong(1000L);
    private final AtomicLong lockIdSeq = new AtomicLong(5000L);
    private final AtomicLong ticketIdSeq = new AtomicLong(9000L);

    private static String lockKey(Long showId, Long seatId) {
        return showId + ":" + seatId;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        bookingsStore.clear();
        seatLocksStore.clear();
        ticketsStore.clear();

        objectMapper = new ObjectMapper().findAndRegisterModules();

        bookingRepository = mock(BookingRepository.class);
        seatLockRepository = mock(SeatLockRepository.class);
        ticketRepository = mock(TicketRepository.class);
        eventServiceGateway = mock(EventServiceGateway.class);
        userServiceClient = mock(UserServiceClient.class);
        bookingEventProducer = mock(BookingEventProducer.class);

        BookingProperties props = new BookingProperties();
        props.setLockMinutes(5);
        props.setMaxSeats(6);

        // Configure stateful BookingRepository mock
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                setId(b, bookingIdSeq.incrementAndGet());
            }
            if (b.getCreatedAt() == null) {
                Field createdAt = Booking.class.getDeclaredField("createdAt");
                createdAt.setAccessible(true);
                createdAt.set(b, LocalDateTime.now());
            }
            bookingsStore.put(b.getId(), b);
            return b;
        });
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                setId(b, bookingIdSeq.incrementAndGet());
            }
            bookingsStore.put(b.getId(), b);
            return b;
        });
        when(bookingRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(bookingsStore.get(inv.<Long>getArgument(0))));
        when(bookingRepository.findByIdForUpdate(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(bookingsStore.get(inv.<Long>getArgument(0))));
        when(bookingRepository.findByIdempotencyKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return bookingsStore.values().stream()
                    .filter(b -> key.equals(b.getIdempotencyKey()))
                    .findFirst();
        });
        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(anyLong())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            return bookingsStore.values().stream()
                    .filter(b -> Objects.equals(uid, b.getUserId()))
                    .toList();
        });
        when(bookingRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<Booking> list = new ArrayList<>();
            for (Long id : ids) {
                Booking b = bookingsStore.get(id);
                if (b != null) list.add(b);
            }
            return list;
        });

        // Configure stateful SeatLockRepository mock
        when(seatLockRepository.deleteIfReleasedOrExpired(anyLong(), anyLong(), any())).thenAnswer(inv -> {
            Long showId = inv.getArgument(0);
            Long seatId = inv.getArgument(1);
            LocalDateTime now = inv.getArgument(2);
            String k = lockKey(showId, seatId);
            SeatLock existing = seatLocksStore.get(k);
            if (existing != null && (existing.getStatus() != SeatLockStatus.LOCKED || !existing.getExpiresAt().isAfter(now))) {
                seatLocksStore.remove(k);
                return 1;
            }
            return 0;
        });
        when(seatLockRepository.tryAcquire(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Long showId = inv.getArgument(0);
            Long seatId = inv.getArgument(1);
            Long userId = inv.getArgument(2);
            Long bookingId = inv.getArgument(3);
            LocalDateTime lockedAt = inv.getArgument(4);
            LocalDateTime expiresAt = inv.getArgument(5);
            String k = lockKey(showId, seatId);
            if (seatLocksStore.containsKey(k)) {
                return 0;
            }
            SeatLock lock = new SeatLock();
            setId(lock, lockIdSeq.incrementAndGet());
            lock.setShowId(showId);
            lock.setSeatId(seatId);
            lock.setUserId(userId);
            lock.setBookingId(bookingId);
            lock.setStatus(SeatLockStatus.LOCKED);
            lock.setLockedAt(lockedAt);
            lock.setExpiresAt(expiresAt);
            seatLocksStore.put(k, lock);
            return 1;
        });
        when(seatLockRepository.findByShowIdAndSeatId(anyLong(), anyLong())).thenAnswer(inv -> {
            Long showId = inv.getArgument(0);
            Long seatId = inv.getArgument(1);
            return Optional.ofNullable(seatLocksStore.get(lockKey(showId, seatId)));
        });
        when(seatLockRepository.findByShowIdAndStatus(anyLong(), any())).thenAnswer(inv -> {
            Long showId = inv.getArgument(0);
            SeatLockStatus status = inv.getArgument(1);
            return seatLocksStore.values().stream()
                    .filter(l -> Objects.equals(showId, l.getShowId()) && l.getStatus() == status)
                    .toList();
        });
        when(seatLockRepository.save(any(SeatLock.class))).thenAnswer(inv -> {
            SeatLock l = inv.getArgument(0);
            seatLocksStore.put(lockKey(l.getShowId(), l.getSeatId()), l);
            return l;
        });
        when(seatLockRepository.attachToBooking(anyLong(), anyLong(), anyLong(), anyLong(), any(), any())).thenAnswer(inv -> {
            Long showId = inv.getArgument(0);
            Long seatId = inv.getArgument(1);
            Long userId = inv.getArgument(2);
            Long bookingId = inv.getArgument(3);
            LocalDateTime expiresAt = inv.getArgument(4);
            LocalDateTime now = inv.getArgument(5);
            SeatLock existing = seatLocksStore.get(lockKey(showId, seatId));
            if (existing != null
                    && Objects.equals(existing.getUserId(), userId)
                    && existing.getBookingId() == null
                    && existing.getStatus() == SeatLockStatus.LOCKED
                    && existing.getExpiresAt().isAfter(now)) {
                existing.setBookingId(bookingId);
                existing.setExpiresAt(expiresAt);
                return 1;
            }
            return 0;
        });
        when(seatLockRepository.releaseByUserAndSeats(anyLong(), anyList(), anyLong(), any())).thenAnswer(inv -> {
            Long showId = inv.getArgument(0);
            List<Long> sids = inv.getArgument(1);
            Long userId = inv.getArgument(2);
            int count = 0;
            for (Long sid : sids) {
                SeatLock lock = seatLocksStore.get(lockKey(showId, sid));
                if (lock != null && Objects.equals(lock.getUserId(), userId) && lock.getBookingId() == null) {
                    lock.setStatus(SeatLockStatus.RELEASED);
                    seatLocksStore.remove(lockKey(showId, sid));
                    count++;
                }
            }
            return count;
        });
        when(seatLockRepository.releaseByBookingId(anyLong(), any())).thenAnswer(inv -> {
            Long bookingId = inv.getArgument(0);
            int count = 0;
            Iterator<Map.Entry<String, SeatLock>> it = seatLocksStore.entrySet().iterator();
            while (it.hasNext()) {
                SeatLock lock = it.next().getValue();
                if (Objects.equals(bookingId, lock.getBookingId())) {
                    lock.setStatus(SeatLockStatus.RELEASED);
                    it.remove();
                    count++;
                }
            }
            return count;
        });
        when(seatLockRepository.makePermanentForBooking(anyLong(), any(), any())).thenAnswer(inv -> {
            Long bookingId = inv.getArgument(0);
            LocalDateTime permanentExpiry = inv.getArgument(1);
            int count = 0;
            for (SeatLock lock : seatLocksStore.values()) {
                if (Objects.equals(bookingId, lock.getBookingId())) {
                    lock.setExpiresAt(permanentExpiry);
                    count++;
                }
            }
            return count;
        });

        // Configure stateful TicketRepository mock
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) {
                setId(t, ticketIdSeq.incrementAndGet());
            }
            ticketsStore.put(t.getId(), t);
            return t;
        });
        when(ticketRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(ticketsStore.get(inv.<Long>getArgument(0))));
        when(ticketRepository.findByBookingId(anyLong())).thenAnswer(inv -> {
            Long bid = inv.getArgument(0);
            return ticketsStore.values().stream()
                    .filter(t -> Objects.equals(bid, t.getBookingId()))
                    .findFirst();
        });

        // Configure EventServiceGateway & UserServiceClient
        when(eventServiceGateway.getShow(501L)).thenReturn(new ShowResponse(
                501L, 10L, "Oppenheimer", "PUBLISHED", 3L, "IMAX Screen 1", "PVR Icon", "Mumbai",
                LocalDate.of(2026, 12, 20), LocalTime.of(19, 30), LocalTime.of(22, 30), ShowStatus.OPEN
        ));
        when(eventServiceGateway.getShowSeats(501L)).thenReturn(List.of(
                new ShowSeatResponse(1L, 501L, 101L, "A1", "A", SeatType.VIP, 1L, "VIP", new BigDecimal("450.00"), "INR", "AVAILABLE"),
                new ShowSeatResponse(2L, 501L, 102L, "A2", "A", SeatType.VIP, 1L, "VIP", new BigDecimal("450.00"), "INR", "AVAILABLE"),
                new ShowSeatResponse(3L, 501L, 103L, "A3", "A", SeatType.REGULAR, 2L, "Regular", new BigDecimal("250.00"), "INR", "BLOCKED")
        ));
        when(userServiceClient.getUserById(101L)).thenReturn(
                new UserDto(101L, "customer101@tickethub.com", "9876543210", "Aarav Mehta", "CUSTOMER")
        );

        // Wire real services and controllers
        SeatLockMapper seatLockMapper = new SeatLockMapper();
        BookingMapper bookingMapper = new BookingMapper(new BookingItemMapper());
        TicketMapper ticketMapper = new TicketMapper();

        SeatLockService seatLockService = new SeatLockServiceImpl(
                seatLockRepository, bookingRepository, eventServiceGateway, seatLockMapper, props
        );
        TicketService ticketService = new TicketServiceImpl(
                ticketRepository, bookingRepository, ticketMapper
        );
        BookingService bookingService = new BookingServiceImpl(
                bookingRepository, seatLockRepository, eventServiceGateway, userServiceClient, ticketService, bookingMapper, props
        );
        PaymentResultService paymentResultService = new PaymentResultServiceImpl(
                bookingRepository, seatLockRepository, ticketService, bookingMapper, eventServiceGateway, userServiceClient, bookingEventProducer
        );

        SeatController seatController = new SeatController(seatLockService);
        BookingController bookingController = new BookingController(bookingService);
        InternalPaymentController internalPaymentController = new InternalPaymentController(paymentResultService);
        TicketController ticketController = new TicketController(ticketService, bookingService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(seatController, bookingController, internalPaymentController, ticketController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    @DisplayName("1. SeatController: GET /api/shows/{showId}/seats, POST /lock (200, 403, 409), POST /release (200, 400)")
    void testSeatControllerEndpoints() throws Exception {
        // 1a. GET /api/shows/501/seats -> 200 OK availability response
        mockMvc.perform(get("/api/shows/501/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].seatId").value(101))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[2].seatId").value(103))
                .andExpect(jsonPath("$.data[2].status").value("UNAVAILABLE"));

        // 1b. POST /api/shows/501/seats/lock -> 200 OK for valid lock
        mockMvc.perform(post("/api/shows/501/seats/lock")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":101,\"seatIds\":[101]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.showId").value(501))
                .andExpect(jsonPath("$.data.userId").value(101))
                .andExpect(jsonPath("$.data.status").value("LOCKED"));

        // 1c. POST /api/shows/501/seats/lock -> 403 Forbidden when X-User-Id mismatches userId for CUSTOMER
        mockMvc.perform(post("/api/shows/501/seats/lock")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9999,\"seatIds\":[102]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 1d. POST /api/shows/501/seats/lock -> 409 Conflict when seat is BLOCKED (seatId 103)
        mockMvc.perform(post("/api/shows/501/seats/lock")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":101,\"seatIds\":[103]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(true));

        // 1e. POST /api/shows/501/seats/lock -> 409 Conflict when seat is locked by another user
        mockMvc.perform(post("/api/shows/501/seats/lock")
                        .header("X-User-Id", "202")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":202,\"seatIds\":[101]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(true));

        // 1f. POST /api/shows/501/seats/release -> 400 Bad Request when seatIds query param is missing
        mockMvc.perform(post("/api/shows/501/seats/release")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":101}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));

        // 1g. POST /api/shows/501/seats/release?seatIds=101 -> 200 OK
        mockMvc.perform(post("/api/shows/501/seats/release")
                        .param("seatIds", "101")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":101}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false));
    }

    @Test
    @DisplayName("2. BookingController: POST /api/bookings, GET /{id}, GET /{id}/status, GET /my-bookings, POST /{id}/cancel")
    void testBookingControllerEndpoints() throws Exception {
        // 2a. POST /api/bookings without Idempotency-Key -> 400 Bad Request
        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Email", "customer101@tickethub.com")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":101,\"showId\":501,\"seatIds\":[101,102]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));

        // 2b. POST /api/bookings with Idempotency-Key, X-User-Id, X-Authenticated-Email, X-Authenticated-Role: CUSTOMER -> 201 Created
        MvcResult createRes = mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "idem-booking-101")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Email", "customer101@tickethub.com")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":101,\"showId\":501,\"seatIds\":[101,102]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.customerEmail").value("customer101@tickethub.com"))
                .andExpect(jsonPath("$.data.customerName").value("Aarav Mehta"))
                .andExpect(jsonPath("$.data.totalAmount").value(900.00))
                .andReturn();

        Long bookingId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("bookingId").asLong();

        // 2c. GET /api/bookings/{bookingId} -> 200 OK for owner
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.bookingId").value(bookingId))
                .andExpect(jsonPath("$.data.userId").value(101));

        // 2d. GET /api/bookings/{bookingId} -> 403 Forbidden for another customer
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("X-User-Id", "9999")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 2e. GET /api/bookings/{bookingId} -> 404 Not Found when booking doesn't exist
        mockMvc.perform(get("/api/bookings/999999")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(true));

        // 2f. GET /api/bookings/{bookingId}/status -> 200 OK for owner
        mockMvc.perform(get("/api/bookings/" + bookingId + "/status")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data").value("PENDING_PAYMENT"));

        // 2g. GET /api/bookings/{bookingId}/status -> 403 Forbidden for another customer
        mockMvc.perform(get("/api/bookings/" + bookingId + "/status")
                        .header("X-User-Id", "9999")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 2h. GET /api/bookings/my-bookings -> 200 OK for own bookings
        mockMvc.perform(get("/api/bookings/my-bookings")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookingId").value(bookingId));

        // 2i. GET /api/bookings/my-bookings?userId=9999 -> 403 Forbidden with X-User-Id: 101
        mockMvc.perform(get("/api/bookings/my-bookings")
                        .param("userId", "9999")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 2j. GET /api/bookings/my-bookings?userId=9999 -> 403 Forbidden with X-Authenticated-User: customer
        mockMvc.perform(get("/api/bookings/my-bookings")
                        .param("userId", "9999")
                        .header("X-Authenticated-User", "customer")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 2k. POST /api/bookings/{bookingId}/cancel -> 403 Forbidden for another customer
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .header("X-User-Id", "9999")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 2l. POST /api/bookings/{bookingId}/cancel -> 200 OK for owner
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("3 & 4. InternalPaymentController (success & failure) and TicketController (/bookings/{id}/ticket, /tickets/{id}, /tickets/{id}/qr)")
    void testInternalPaymentAndTicketControllerEndpoints() throws Exception {
        // Create booking for seat 101
        MvcResult createRes = mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "idem-pay-success-1")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Email", "customer101@tickethub.com")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":101,\"showId\":501,\"seatIds\":[101]}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long bookingId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("bookingId").asLong();

        // 3a. POST /api/internal/payments/{bookingId}/success -> 200 OK, CONFIRMED, generates ticket, publishes BookingConfirmedEvent
        mockMvc.perform(post("/api/internal/payments/" + bookingId + "/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"pay_rzp_success_001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        ArgumentCaptor<BookingConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(bookingEventProducer).publish(eventCaptor.capture());
        BookingConfirmedEvent event = eventCaptor.getValue();
        assertNotNull(event.getTicketId());
        assertNotNull(event.getTicketNumber());
        assertTrue(event.getTicketNumber().startsWith("TKT-"));
        assertNotNull(event.getQrCode());
        assertTrue(event.getQrCode().startsWith("TICKETHUB:BK-"));
        assertEquals("customer101@tickethub.com", event.getRecipientEmail());

        Long ticketId = event.getTicketId();

        // 4a. GET /api/bookings/{bookingId}/ticket -> 200 OK for owner
        mockMvc.perform(get("/api/bookings/" + bookingId + "/ticket")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.ticketId").value(ticketId))
                .andExpect(jsonPath("$.data.bookingId").value(bookingId))
                .andExpect(jsonPath("$.data.ticketNumber").value(event.getTicketNumber()));

        // 4b. GET /api/bookings/{bookingId}/ticket -> 403 Forbidden for another customer
        mockMvc.perform(get("/api/bookings/" + bookingId + "/ticket")
                        .header("X-User-Id", "9999")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 4c. GET /api/tickets/{ticketId} -> 200 OK for owner
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.ticketId").value(ticketId))
                .andExpect(jsonPath("$.data.qrCode").value(event.getQrCode()));

        // 4d. GET /api/tickets/{ticketId} -> 403 Forbidden for another customer
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("X-User-Id", "9999")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 4e. GET /api/tickets/{ticketId}/qr -> 200 OK returning image/png bytes for owner
        MvcResult qrResult = mockMvc.perform(get("/api/tickets/" + ticketId + "/qr")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] pngBytes = qrResult.getResponse().getContentAsByteArray();
        assertTrue(pngBytes.length > 8, "PNG payload should be non-empty");
        assertEquals((byte) 0x89, pngBytes[0]);
        assertEquals((byte) 0x50, pngBytes[1]); // 'P'
        assertEquals((byte) 0x4E, pngBytes[2]); // 'N'
        assertEquals((byte) 0x47, pngBytes[3]); // 'G'

        // 4f. GET /api/tickets/{ticketId}/qr -> 403 Forbidden for another customer
        mockMvc.perform(get("/api/tickets/" + ticketId + "/qr")
                        .header("X-User-Id", "9999")
                        .header("X-Authenticated-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true));

        // 3b. POST /api/internal/payments/{bookingId2}/failure -> 200 OK, transitions booking to PAYMENT_FAILED, releases locks
        MvcResult createRes2 = mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "idem-pay-fail-2")
                        .header("X-User-Id", "101")
                        .header("X-Authenticated-Email", "customer101@tickethub.com")
                        .header("X-Authenticated-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":101,\"showId\":501,\"seatIds\":[102]}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long bookingId2 = objectMapper.readTree(createRes2.getResponse().getContentAsString())
                .path("data").path("bookingId").asLong();

        mockMvc.perform(post("/api/internal/payments/" + bookingId2 + "/failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"pay_rzp_fail_002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.status").value("PAYMENT_FAILED"));

        // Verify lock on seat 102 was released and is AVAILABLE again
        assertFalse(seatLocksStore.containsKey(lockKey(501L, 102L)), "Seat 102 lock should be released after payment failure");
    }
}

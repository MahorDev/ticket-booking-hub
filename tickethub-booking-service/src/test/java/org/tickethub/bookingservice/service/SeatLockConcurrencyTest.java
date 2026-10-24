package org.tickethub.bookingservice.service;

import org.junit.jupiter.api.Test;
import org.tickethub.bookingservice.client.EventServiceClient;
import org.tickethub.bookingservice.client.dto.SeatType;
import org.tickethub.bookingservice.client.dto.ShowSeatResponse;
import org.tickethub.bookingservice.config.BookingProperties;
import org.tickethub.bookingservice.dto.request.SeatLockRequest;
import org.tickethub.bookingservice.dto.response.SeatLockResponse;
import org.tickethub.bookingservice.entity.SeatLock;
import org.tickethub.bookingservice.exception.ConflictException;
import org.tickethub.bookingservice.mapper.SeatLockMapper;
import org.tickethub.bookingservice.repository.BookingRepository;
import org.tickethub.bookingservice.repository.SeatLockRepository;
import org.tickethub.bookingservice.service.impl.SeatLockServiceImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeatLockConcurrencyTest {

    @Test
    void exactlyOneConcurrentCallerWinsAtomicSeatAcquisition() throws Exception {
        SeatLockRepository repository = mock(SeatLockRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        SeatLockMapper mapper = mock(SeatLockMapper.class);

        BookingProperties properties = new BookingProperties();
        properties.setMaxSeats(6);
        properties.setLockMinutes(5);

        when(eventClient.getShowSeats(501L)).thenReturn(List.of(
                new ShowSeatResponse(
                        9001L, 501L, 101L, "A1", "A",
                        SeatType.REGULAR, 1L, "Standard", BigDecimal.valueOf(250),
                        "INR", "AVAILABLE"
                )
        ));
        when(repository.deleteIfReleasedOrExpired(anyLong(), anyLong(), any()))
                .thenReturn(0);
        when(repository.findByShowIdAndSeatId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        AtomicInteger winners = new AtomicInteger();
        when(repository.tryAcquire(
                anyLong(), anyLong(), anyLong(), any(),
                any(), any(), any(), any()
        )).thenAnswer(invocation ->
                winners.getAndIncrement() == 0 ? 1 : 0
        );

        SeatLockServiceImpl service = new SeatLockServiceImpl(
                repository, bookingRepository, eventClient, mapper, properties
        );

        when(mapper.toLockResponse(
                anyLong(), anyLong(), anyList(), any(), any()
        )).thenAnswer(invocation ->
                new SeatLockResponse(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4)
                )
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() ->
                    service.lock(
                            501L,
                            new SeatLockRequest(1001L, List.of(101L))
                    )
            );

            Future<?> second = executor.submit(() ->
                    service.lock(
                            501L,
                            new SeatLockRequest(1002L, List.of(101L))
                    )
            );

            int success = 0;
            int conflicts = 0;

            for (Future<?> future : List.of(first, second)) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    success++;
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof ConflictException) {
                        conflicts++;
                    } else {
                        throw ex;
                    }
                }
            }

            assertEquals(1, success);
            assertEquals(1, conflicts);
            assertEquals(2, winners.get());
        } finally {
            executor.shutdownNow();
        }
    }
}

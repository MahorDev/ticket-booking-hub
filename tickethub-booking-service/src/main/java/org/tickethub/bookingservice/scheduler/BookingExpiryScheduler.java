package org.tickethub.bookingservice.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tickethub.bookingservice.service.SeatLockService;

@Component
public class BookingExpiryScheduler {

    private final SeatLockService seatLockService;

    public BookingExpiryScheduler(SeatLockService seatLockService) {
        this.seatLockService = seatLockService;
    }

    @Scheduled(fixedDelayString = "${booking.expiry-check-ms:10000}")
    public void expireBookingsAndLocks() {
        seatLockService.expireLocks();
    }
}

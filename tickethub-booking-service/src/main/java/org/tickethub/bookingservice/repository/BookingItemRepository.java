package org.tickethub.bookingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tickethub.bookingservice.entity.BookingItem;
import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    List<BookingItem> findByBookingId(Long bookingId);
}

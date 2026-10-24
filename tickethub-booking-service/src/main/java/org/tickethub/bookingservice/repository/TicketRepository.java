package org.tickethub.bookingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tickethub.bookingservice.entity.Ticket;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByBookingId(Long bookingId);
}

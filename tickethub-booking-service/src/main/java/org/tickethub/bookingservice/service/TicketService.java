package org.tickethub.bookingservice.service;

import org.tickethub.bookingservice.dto.response.TicketResponse;

public interface TicketService {
    TicketResponse getById(Long ticketId);
    TicketResponse getByBookingId(Long bookingId);
    TicketResponse generateForBooking(Long bookingId);
}

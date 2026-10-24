package org.tickethub.bookingservice.mapper;

import org.springframework.stereotype.Component;
import org.tickethub.bookingservice.dto.response.TicketResponse;
import org.tickethub.bookingservice.entity.Ticket;

@Component
public class TicketMapper {

    public TicketResponse toResponse(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        return new TicketResponse(
                ticket.getId(),
                ticket.getBookingId(),
                ticket.getTicketNumber(),
                ticket.getQrCode(),
                ticket.getStatus(),
                ticket.getIssuedAt()
        );
    }
}

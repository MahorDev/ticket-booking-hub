package org.tickethub.bookingservice.dto.response;

import org.tickethub.bookingservice.entity.enums.TicketStatus;
import java.time.LocalDateTime;

public record TicketResponse(
    Long ticketId,
    Long bookingId,
    String ticketNumber,
    String qrCode,
    TicketStatus status,
    LocalDateTime issuedAt
) {}

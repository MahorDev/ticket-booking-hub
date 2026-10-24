package org.tickethub.bookingservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tickethub.bookingservice.dto.response.TicketResponse;
import org.tickethub.bookingservice.entity.Booking;
import org.tickethub.bookingservice.entity.Ticket;
import org.tickethub.bookingservice.entity.enums.BookingStatus;
import org.tickethub.bookingservice.entity.enums.TicketStatus;
import org.tickethub.bookingservice.exception.ConflictException;
import org.tickethub.bookingservice.exception.NotFoundException;
import org.tickethub.bookingservice.repository.BookingRepository;
import org.tickethub.bookingservice.mapper.TicketMapper;
import org.tickethub.bookingservice.repository.TicketRepository;
import org.tickethub.bookingservice.service.TicketService;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final BookingRepository bookingRepository;
    private final TicketMapper ticketMapper;

    public TicketServiceImpl(TicketRepository ticketRepository, BookingRepository bookingRepository, TicketMapper ticketMapper) {
        this.ticketRepository = ticketRepository;
        this.bookingRepository = bookingRepository;
        this.ticketMapper = ticketMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getById(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .map(ticketMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getByBookingId(Long bookingId) {
        return ticketRepository.findByBookingId(bookingId)
                .map(ticketMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Ticket not found for booking: " + bookingId));
    }

    @Override
    @Transactional
    public TicketResponse generateForBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Ticket can only be generated for a CONFIRMED booking");
        }

        Ticket existing = ticketRepository.findByBookingId(bookingId).orElse(null);
        if (existing != null) {
            return ticketMapper.toResponse(existing);
        }

        Ticket ticket = new Ticket();
        ticket.setBookingId(bookingId);
        ticket.setTicketNumber("TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        ticket.setQrCode("TICKETHUB:" + booking.getBookingReference());
        ticket.setStatus(TicketStatus.ACTIVE);
        ticket.setIssuedAt(LocalDateTime.now());

        return ticketMapper.toResponse(ticketRepository.save(ticket));
    }

}

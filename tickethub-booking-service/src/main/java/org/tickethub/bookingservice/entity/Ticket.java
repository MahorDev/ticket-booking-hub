package org.tickethub.bookingservice.entity;

import jakarta.persistence.*;
import org.tickethub.bookingservice.entity.enums.TicketStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_ticket_booking", columnNames = "booking_id"))
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 50)
    private String ticketNumber;

    @Column(name = "qr_code", nullable = false, length = 500)
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        issuedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getTicketNumber() { return ticketNumber; }
    public String getQrCode() { return qrCode; }
    public TicketStatus getStatus() { return status; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package org.tickethub.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingConfirmedEvent {
    // Event metadata for Saga & Inbox Idempotency
    @JsonAlias({"eventMessageId", "messageId"})
    private String eventMessageId;
    private String eventType;
    private Long aggregateVersion;
    private java.time.LocalDateTime eventTimestamp;

    // Booking information
    private Long bookingId;
    private String bookingReference;

    // Customer information
    private Long userId;
    private String recipientName;
    private String recipientEmail;

    // Event / movie information
    private Long eventId;
    @JsonAlias("movieTitle")
    private String eventTitle;

    // Show information
    private Long showId;
    @JsonAlias("date")
    private LocalDate showDate;
    @JsonAlias("time")
    private LocalTime startTime;
    private LocalTime endTime;

    // Venue / screen information
    @JsonAlias("venue")
    private String venueName;
    private String venueAddress;
    @JsonAlias("screen")
    private String screenName;

    // Seat information
    private List<SeatInfo> seats;
    private String seatCategory;

    // Payment information
    @JsonAlias({"totalAmount", "amount"})
    private BigDecimal totalAmount;
    private String currency;
    private String paymentStatus;
    private String paymentReference;

    // Ticket information
    private Long ticketId;
    private String ticketNumber;
    private String ticketStatus;
    private String qrCode;
    private java.time.LocalDateTime issuedAt;
}
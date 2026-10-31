package org.tickethub.bookingservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingConfirmedEvent {
    // Event metadata for Saga & Inbox Idempotency
    private String eventMessageId;
    private String eventType;
    private Long aggregateVersion;
    private LocalDateTime eventTimestamp;

    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private String recipientEmail;
    private String recipientName;
    private Long eventId;
    private String movieTitle;
    private String eventTitle;
    private Long showId;
    private LocalDate date;
    private LocalDate showDate;
    private LocalTime time;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venue;
    private String venueName;
    private String venueAddress;
    private String screen;
    private String screenName;
    private List<SeatInfo> seats;
    private BigDecimal totalAmount;
    private String currency;
    private String paymentStatus;
    private String paymentReference;
    private Long ticketId;
    private String ticketNumber;
    private String ticketStatus;
    private String qrCode;
    private LocalDateTime issuedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SeatInfo {
        private Long seatId;
        private String seatNumber;
        private String rowName;
        private String seatType;
        private BigDecimal price;

        public SeatInfo(String seatNumber, String seatType) {
            this.seatNumber = seatNumber;
            this.seatType = seatType;
        }
    }
}

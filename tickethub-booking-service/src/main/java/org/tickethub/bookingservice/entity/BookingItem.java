package org.tickethub.bookingservice.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_items",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_booking_item_seat",
           columnNames = {"booking_id", "seat_id"}))
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "seat_number", length = 30)
    private String seatNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public Long getSeatId() { return seatId; }
    public String getSeatNumber() { return seatNumber; }
    public BigDecimal getPrice() { return price; }

    public void setBooking(Booking booking) { this.booking = booking; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public void setPrice(BigDecimal price) { this.price = price; }
}

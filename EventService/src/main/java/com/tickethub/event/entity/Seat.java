package com.tickethub.event.entity;

import com.tickethub.event.enums.SeatType;
import com.tickethub.event.enums.SeatSection;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(name = "uk_seat_screen_number", columnNames = {"screen_id", "seat_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;

    @Column(name = "seat_number", nullable = false, length = 30)
    private String seatNumber;

    @Column(name = "row_name", nullable = false, length = 20)
    private String rowName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatType seatType;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 20)
    private SeatSection section;

    @Column(name = "base_price", precision = 10, scale = 2)
    private BigDecimal basePrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;
}

package com.tickethub.event.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "screens", uniqueConstraints = @UniqueConstraint(name = "uk_screen_venue_name", columnNames = {"venue_id", "screen_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long screenId;

    @Column(name = "screen_name", nullable = false, length = 100)
    private String screenName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;
}

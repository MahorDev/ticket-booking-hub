package com.tickethub.event.entity;

import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.EventType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType eventType;

    @Column(nullable = false, length = 50)
    private String language;

    @Column(nullable = false)
    private Integer duration;

    @Column(length = 20)
    private String rating;

    @Column(length = 100)
    private String genre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;
}

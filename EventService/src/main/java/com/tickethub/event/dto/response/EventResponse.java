package com.tickethub.event.dto.response;

import com.tickethub.event.enums.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {

    private Long eventId;
    private String title;
    private String description;
    private EventType eventType;
    private String language;
    private Integer duration;
    private String rating;
    private String genre;
    private EventStatus status;
    private String posterUrl;
}

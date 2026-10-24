package com.tickethub.event.mapper;

import com.tickethub.event.dto.request.EventRequest;
import com.tickethub.event.dto.response.EventResponse;
import com.tickethub.event.entity.Event;
import com.tickethub.event.enums.EventStatus;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public Event toEntity(EventRequest r) {
        return Event.builder().title(r.getTitle()).description(r.getDescription()).eventType(r.getEventType()).language(r.getLanguage()).duration(r.getDuration()).rating(r.getRating()).genre(r.getGenre()).status(EventStatus.DRAFT).build();
    }

    public void update(Event e, EventRequest r) {
        e.setTitle(r.getTitle());
        e.setDescription(r.getDescription());
        e.setEventType(r.getEventType());
        e.setLanguage(r.getLanguage());
        e.setDuration(r.getDuration());
        e.setRating(r.getRating());
        e.setGenre(r.getGenre());
    }

    public EventResponse toResponse(Event e) {
        return EventResponse.builder().eventId(e.getEventId()).title(e.getTitle()).description(e.getDescription()).eventType(e.getEventType()).language(e.getLanguage()).duration(e.getDuration()).rating(e.getRating()).genre(e.getGenre()).status(e.getStatus()).posterUrl(e.getPosterUrl()).build();
    }
}

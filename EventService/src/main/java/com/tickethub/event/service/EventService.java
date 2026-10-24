package com.tickethub.event.service;

import com.tickethub.event.dto.request.EventRequest;
import com.tickethub.event.dto.response.EventResponse;
import com.tickethub.event.dto.response.PageResponse;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.EventType;
import org.springframework.data.domain.Pageable;

public interface EventService {

    EventResponse create(EventRequest request);

    PageResponse<EventResponse> getAll(
            Pageable pageable,
            String title,
            EventType eventType,
            String genre,
            String language,
            EventStatus status,
            boolean publishedOnly
    );

    EventResponse getById(Long eventId, boolean publishedOnly);

    EventResponse changeStatus(Long eventId, EventStatus newStatus);

    EventResponse update(
            Long eventId,
            EventRequest request
    );

    void delete(Long eventId);
}
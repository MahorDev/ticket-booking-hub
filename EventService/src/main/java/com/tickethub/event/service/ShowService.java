package com.tickethub.event.service;

import com.tickethub.event.dto.request.ShowRequest;
import com.tickethub.event.dto.response.*;
import com.tickethub.event.enums.ShowStatus;
import org.springframework.data.domain.Pageable;

public interface ShowService {
    ShowResponse create(ShowRequest r);

    PageResponse<ShowResponse> getAll(Pageable p);

    PageResponse<ShowResponse> getByEvent(Long eventId, Pageable p);

    default PageResponse<ShowResponse> getByEvent(Long eventId, Pageable p, boolean publishedOnly) {
        return getByEvent(eventId, p);
    }

    PageResponse<ShowResponse> getByScreen(Long screenId, Pageable p);

    ShowResponse getById(Long id);

    ShowResponse update(Long id, ShowRequest r);

    ShowResponse changeStatus(Long id, ShowStatus newStatus);

    void delete(Long id);
}

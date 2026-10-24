package com.tickethub.event.service;

import com.tickethub.event.dto.request.ScreenRequest;
import com.tickethub.event.dto.response.*;
import org.springframework.data.domain.Pageable;

public interface ScreenService {
    ScreenResponse create(ScreenRequest r);

    PageResponse<ScreenResponse> getAll(Pageable p);

    PageResponse<ScreenResponse> getByVenue(Long venueId, Pageable p);

    ScreenResponse getById(Long id);

    ScreenResponse update(Long id, ScreenRequest r);

    void delete(Long id);
}

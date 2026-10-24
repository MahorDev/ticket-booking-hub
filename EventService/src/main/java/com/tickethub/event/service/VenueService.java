package com.tickethub.event.service;

import com.tickethub.event.dto.request.VenueRequest;
import com.tickethub.event.dto.response.*;
import org.springframework.data.domain.Pageable;

public interface VenueService {
    VenueResponse create(VenueRequest r);

    PageResponse<VenueResponse> getAll(Pageable p);

    VenueResponse getById(Long id);

    VenueResponse update(Long id, VenueRequest r);

    void delete(Long id);
}

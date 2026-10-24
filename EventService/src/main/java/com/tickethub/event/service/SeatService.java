package com.tickethub.event.service;

import com.tickethub.event.dto.request.SeatRequest;
import com.tickethub.event.dto.response.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SeatService {
    SeatResponse create(SeatRequest r);

    List<SeatResponse> createBulk(List<SeatRequest> requests);

    PageResponse<SeatResponse> getAll(Pageable p);

    PageResponse<SeatResponse> getByScreen(Long screenId, Pageable p);

    SeatResponse getById(Long id);

    SeatResponse update(Long id, SeatRequest r);

    void delete(Long id);
}

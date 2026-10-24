package com.tickethub.event.service;

import com.tickethub.event.dto.request.ShowSeatRequest;
import com.tickethub.event.dto.response.*;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ShowSeatService {
    ShowSeatResponse create(ShowSeatRequest r);

    PageResponse<ShowSeatResponse> getByShow(Long showId, Pageable p);

    List<ShowSeatResponse> getAllByShow(Long showId);

    ShowSeatResponse getById(Long id);

    ShowSeatResponse update(Long id, ShowSeatRequest r);

    void delete(Long id);
}

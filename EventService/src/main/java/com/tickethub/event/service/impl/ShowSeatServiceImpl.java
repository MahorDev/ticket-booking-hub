package com.tickethub.event.service.impl;

import com.tickethub.event.dto.request.ShowSeatRequest;
import com.tickethub.event.dto.response.*;
import com.tickethub.event.entity.*;
import com.tickethub.event.exception.*;
import com.tickethub.event.mapper.ShowSeatMapper;
import com.tickethub.event.repository.*;
import com.tickethub.event.service.ShowSeatService;
import com.tickethub.event.util.PageableValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowSeatServiceImpl implements ShowSeatService {
    private final ShowSeatRepository repo;
    private final ShowRepository showRepo;
    private final SeatRepository seatRepo;
    private final ShowSeatMapper mapper;
    private final PageableValidator validator;
    private final Set<String> allowed = Set.of("showSeatId", "price", "status");

    @Transactional
    public ShowSeatResponse create(ShowSeatRequest r) {
        Show show = show(r.getShowId());
        Seat seat = seat(r.getSeatId());
        validateSeatForShow(show, seat);
        if (repo.existsByShowShowIdAndSeatSeatId(r.getShowId(), r.getSeatId()))
            throw new BusinessException("This seat is already configured for the show.");
        return mapper.toResponse(repo.save(ShowSeat.builder().show(show).seat(seat).price(priceFor(show, seat, r)).status(r.getStatus()).build()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ShowSeatResponse> getByShow(Long id, Pageable p) {
        show(id);
        validator.validate(p, allowed);
        return PageResponse.from(repo.findByShowShowId(id, p).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<ShowSeatResponse> getAllByShow(Long showId) {
        show(showId);
        return repo.findAllByShowShowIdOrderByShowSeatId(showId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ShowSeatResponse getById(Long id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public ShowSeatResponse update(Long id, ShowSeatRequest r) {
        ShowSeat ss = find(id);
        Show show = show(r.getShowId());
        Seat seat = seat(r.getSeatId());
        validateSeatForShow(show, seat);
        if ((!ss.getShow().getShowId().equals(show.getShowId()) || !ss.getSeat().getSeatId().equals(seat.getSeatId())) && repo.existsByShowShowIdAndSeatSeatId(show.getShowId(), seat.getSeatId()))
            throw new BusinessException("This seat is already configured for the show.");
        ss.setShow(show);
        ss.setSeat(seat);
        ss.setPrice(priceFor(show, seat, r));
        ss.setStatus(r.getStatus());
        return mapper.toResponse(ss);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("ShowSeat not found: " + id);
        repo.deleteById(id);
    }

    private ShowSeat find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("ShowSeat not found: " + id));
    }

    private Show show(Long id) {
        return showRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Show not found: " + id));
    }

    private Seat seat(Long id) {
        return seatRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + id));
    }

    // If the request has no price, fall back to the seat's base price or the show's ticket price
    private java.math.BigDecimal priceFor(Show show, Seat seat, ShowSeatRequest r) {
        if (r.getPrice() != null) {
            return r.getPrice();
        }
        return (seat != null && seat.getBasePrice() != null) ? seat.getBasePrice() : show.getTicketPrice();
    }

    private void validateSeatForShow(Show show, Seat seat) {
        if (!show.getScreen().getScreenId().equals(seat.getScreen().getScreenId()))
            throw new BusinessException("Seat does not belong to the show's screen.");
    }
}

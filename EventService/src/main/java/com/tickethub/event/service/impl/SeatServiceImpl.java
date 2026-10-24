package com.tickethub.event.service.impl;

import com.tickethub.event.dto.request.SeatRequest;
import com.tickethub.event.dto.response.*;
import com.tickethub.event.entity.*;
import com.tickethub.event.exception.*;
import com.tickethub.event.mapper.SeatMapper;
import com.tickethub.event.repository.*;
import com.tickethub.event.service.SeatService;
import com.tickethub.event.util.PageableValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tickethub.event.enums.ShowSeatStatus;
import com.tickethub.event.enums.ShowStatus;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {
    private final SeatRepository repo;
    private final ScreenRepository screenRepo;
    private final ShowSeatRepository showSeatRepo;
    private final ShowRepository showRepo;
    private final SeatMapper mapper;
    private final PageableValidator validator;
    private final Set<String> allowed = Set.of("seatId", "seatNumber", "rowName", "seatType", "section", "basePrice");

    @Transactional
    public SeatResponse create(SeatRequest r) {
        Screen s = screen(r.getScreenId());
        if (repo.existsByScreenScreenIdAndSeatNumberIgnoreCase(r.getScreenId(), r.getSeatNumber()))
            throw new BusinessException("Seat number already exists in this screen.");
        Seat savedSeat = repo.save(Seat.builder().seatNumber(r.getSeatNumber()).rowName(r.getRowName()).seatType(r.getSeatType())
                .section(r.getSection() != null ? r.getSection() : com.tickethub.event.enums.SeatSection.LOWER).basePrice(r.getBasePrice()).screen(s).build());

        // Automatically propagate this new seat to any scheduled or open shows on this screen
        List<Show> activeShows = showRepo.findAllByScreenScreenId(s.getScreenId());
        if (activeShows != null) {
            for (Show show : activeShows) {
                if ((show.getStatus() == ShowStatus.SCHEDULED || show.getStatus() == ShowStatus.OPEN)
                        && !showSeatRepo.existsByShowShowIdAndSeatSeatId(show.getShowId(), savedSeat.getSeatId())) {
                    showSeatRepo.save(ShowSeat.builder()
                            .show(show)
                            .seat(savedSeat)
                            .price(savedSeat.getBasePrice() != null ? savedSeat.getBasePrice() : show.getTicketPrice())
                            .status(ShowSeatStatus.AVAILABLE)
                            .build());
                }
            }
        }

        return mapper.toResponse(savedSeat);
    }

    @Override
    @Transactional
    public List<SeatResponse> createBulk(List<SeatRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<SeatResponse> responses = new java.util.ArrayList<>();
        for (SeatRequest r : requests) {
            responses.add(create(r));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public PageResponse<SeatResponse> getAll(Pageable p) {
        validator.validate(p, allowed);
        return PageResponse.from(repo.findAll(p).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<SeatResponse> getByScreen(Long id, Pageable p) {
        screen(id);
        validator.validate(p, allowed);
        return PageResponse.from(repo.findByScreenScreenId(id, p).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public SeatResponse getById(Long id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public SeatResponse update(Long id, SeatRequest r) {
        Seat s = find(id);
        Screen sc = screen(r.getScreenId());
        if (!s.getScreen().getScreenId().equals(sc.getScreenId()) && showSeatRepo.existsBySeatSeatId(id)) {
            throw new BusinessException("Cannot move seat to another screen while show-seat records exist.");
        }
        if ((!s.getSeatNumber().equalsIgnoreCase(r.getSeatNumber()) || !s.getScreen().getScreenId().equals(sc.getScreenId())) && repo.existsByScreenScreenIdAndSeatNumberIgnoreCase(sc.getScreenId(), r.getSeatNumber()))
            throw new BusinessException("Seat number already exists in this screen.");
        s.setSeatNumber(r.getSeatNumber());
        s.setRowName(r.getRowName());
        s.setSeatType(r.getSeatType());
        s.setSection(r.getSection() != null ? r.getSection() : (s.getSection() != null ? s.getSection() : com.tickethub.event.enums.SeatSection.LOWER));
        s.setBasePrice(r.getBasePrice());
        s.setScreen(sc);
        return mapper.toResponse(s);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Seat not found: " + id);
        if (showSeatRepo.existsBySeatSeatId(id))
            throw new BusinessException("Cannot delete seat because show-seat records exist.");
        repo.deleteById(id);
    }

    private Seat find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + id));
    }

    private Screen screen(Long id) {
        return screenRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + id));
    }
}

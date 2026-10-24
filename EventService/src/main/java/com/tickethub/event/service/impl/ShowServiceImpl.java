package com.tickethub.event.service.impl;

import com.tickethub.event.dto.request.ShowRequest;
import com.tickethub.event.dto.response.PageResponse;
import com.tickethub.event.dto.response.ShowResponse;
import com.tickethub.event.entity.Event;
import com.tickethub.event.entity.Screen;
import com.tickethub.event.entity.Show;
import com.tickethub.event.entity.ShowSeat;
import com.tickethub.event.entity.Seat;
import com.tickethub.event.enums.ShowSeatStatus;
import com.tickethub.event.enums.ShowStatus;
import com.tickethub.event.exception.BusinessException;
import com.tickethub.event.exception.ResourceNotFoundException;
import com.tickethub.event.mapper.ShowMapper;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.ScreenRepository;
import com.tickethub.event.repository.ShowRepository;
import com.tickethub.event.repository.ShowSeatRepository;
import com.tickethub.event.repository.SeatRepository;
import com.tickethub.event.service.ShowService;
import com.tickethub.event.util.PageableValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShowServiceImpl implements ShowService {

    private final ShowRepository showRepository;
    private final EventRepository eventRepository;
    private final ScreenRepository screenRepository;
    private final ShowSeatRepository showSeatRepository;
    private final SeatRepository seatRepository;
    private final ShowMapper showMapper;
    private final PageableValidator pageableValidator;

    private final Set<String> allowedSortFields = Set.of(
            "showId",
            "showDate",
            "startTime",
            "endTime",
            "status"
    );

    // =========================
    // CREATE SHOW
    // =========================

    @Override
    @Transactional
    public ShowResponse create(ShowRequest request) {

        log.info(
                "Creating show for eventId={} and screenId={}",
                request.getEventId(),
                request.getScreenId()
        );

        validateTime(request);

        Event event = findEvent(request.getEventId());
        if (event.getStatus() != com.tickethub.event.enums.EventStatus.PUBLISHED) {
            throw new BusinessException("Show can only be created for a PUBLISHED event.");
        }
        validateShowDurationAgainstEvent(request, event);
        Screen screen = findScreen(request.getScreenId());

        if (request.getShowDate().isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("Show date cannot be in the past.");
        }
        if (request.getShowDate().isEqual(java.time.LocalDate.now())
                && request.getStartTime().isBefore(java.time.LocalTime.now())) {
            throw new BusinessException("Show start time cannot be in the past.");
        }

        // Check whether another show overlaps
        checkOverlap(request, null);

        Show show = Show.builder()
                .event(event)
                .screen(screen)
                .showDate(request.getShowDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .ticketPrice(request.getTicketPrice())
                .currency(request.getCurrency())
                .status(ShowStatus.SCHEDULED) // every new show starts as SCHEDULED
                .build();

        Show savedShow = showRepository.save(show);

        List<Seat> screenSeats = seatRepository.findAllByScreenScreenId(screen.getScreenId());
        if (screenSeats.isEmpty()) {
            throw new BusinessException("Cannot create show because the selected screen has no seats configured.");
        }

        List<ShowSeat> showSeats = screenSeats.stream()
                .map(seat -> ShowSeat.builder()
                        .show(savedShow)
                        .seat(seat)
                        .price(seat.getBasePrice() != null ? seat.getBasePrice() : savedShow.getTicketPrice())
                        .status(ShowSeatStatus.AVAILABLE)
                        .build())
                .toList();
        showSeatRepository.saveAll(showSeats);

        log.info("Show created successfully with id={} and {} show seats", savedShow.getShowId(), showSeats.size());

        return showMapper.toResponse(savedShow);
    }

    // =========================
    // GET ALL SHOWS
    // =========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> getAll(Pageable pageable) {

        log.info("Fetching all shows");

        pageableValidator.validate(pageable, allowedSortFields);

        return PageResponse.from(
                showRepository.findAll(pageable)
                        .map(showMapper::toResponse)
        );
    }

    // =========================
    // GET SHOWS BY EVENT
    // =========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> getByEvent(
            Long eventId,
            Pageable pageable) {
        return getByEvent(eventId, pageable, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> getByEvent(
            Long eventId,
            Pageable pageable,
            boolean publishedOnly) {

        log.info("Fetching shows for eventId={}", eventId);

        Event event = findEvent(eventId);
        if (publishedOnly && event.getStatus() != com.tickethub.event.enums.EventStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }

        pageableValidator.validate(pageable, allowedSortFields);

        return PageResponse.from(
                showRepository
                        .findByEventEventId(eventId, pageable)
                        .map(showMapper::toResponse)
        );
    }

    // =========================
    // GET SHOWS BY SCREEN
    // =========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> getByScreen(
            Long screenId,
            Pageable pageable) {

        log.info("Fetching shows for screenId={}", screenId);

        findScreen(screenId);

        pageableValidator.validate(pageable, allowedSortFields);

        return PageResponse.from(
                showRepository
                        .findByScreenScreenId(screenId, pageable)
                        .map(showMapper::toResponse)
        );
    }

    // =========================
    // GET SHOW BY ID
    // =========================

    @Override
    @Transactional(readOnly = true)
    public ShowResponse getById(Long showId) {

        log.info("Fetching show with id={}", showId);

        Show show = findShow(showId);

        return showMapper.toResponse(show);
    }

    // =========================
    // UPDATE SHOW
    // =========================

    @Override
    @Transactional
    public ShowResponse update(
            Long showId,
            ShowRequest request) {

        log.info("Updating show with id={}", showId);

        Show show = findShow(showId);

        if (show.getStatus() == ShowStatus.CANCELLED || show.getStatus() == ShowStatus.COMPLETED) {
            throw new BusinessException("Cannot update a show that is " + show.getStatus() + ".");
        }

        validateTime(request);

        Event event = findEvent(request.getEventId());
        if (event.getStatus() != com.tickethub.event.enums.EventStatus.PUBLISHED) {
            throw new BusinessException("Show can only be assigned to a PUBLISHED event.");
        }
        validateShowDurationAgainstEvent(request, event);
        Screen screen = findScreen(request.getScreenId());

        if (request.getShowDate().isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("Show date cannot be in the past.");
        }
        if (request.getShowDate().isEqual(java.time.LocalDate.now())
                && request.getStartTime().isBefore(java.time.LocalTime.now())) {
            throw new BusinessException("Show start time cannot be in the past.");
        }

        if (!show.getScreen().getScreenId().equals(screen.getScreenId()) && showSeatRepository.existsByShowShowId(showId)) {
            throw new BusinessException("Cannot change the screen after show seats have been created.");
        }

        // Ignore the current show while checking overlap
        checkOverlap(request, showId);

        java.math.BigDecimal oldTicketPrice = show.getTicketPrice();

        show.setEvent(event);
        show.setScreen(screen);
        show.setShowDate(request.getShowDate());
        show.setStartTime(request.getStartTime());
        show.setEndTime(request.getEndTime());
        show.setTicketPrice(request.getTicketPrice());
        show.setCurrency(request.getCurrency());

        if (request.getTicketPrice() != null
                && (oldTicketPrice == null || oldTicketPrice.compareTo(request.getTicketPrice()) != 0)) {
            List<ShowSeat> showSeats = showSeatRepository.findAllByShowShowIdOrderByShowSeatId(showId);
            if (showSeats != null) {
                for (ShowSeat ss : showSeats) {
                    boolean usesShowPrice = (ss.getSeat() != null && ss.getSeat().getBasePrice() == null)
                            || (oldTicketPrice != null && ss.getPrice() != null && ss.getPrice().compareTo(oldTicketPrice) == 0);
                    if (usesShowPrice) {
                        ss.setPrice(request.getTicketPrice());
                    }
                }
            }
        }

        log.info("Show updated successfully with id={}", showId);

        return showMapper.toResponse(show);
    }

    // =========================
    // CHANGE STATUS (SCHEDULED -> OPEN -> SOLD_OUT / CANCELLED / COMPLETED)
    // =========================

    @Override
    @Transactional
    public ShowResponse changeStatus(Long showId, ShowStatus newStatus) {

        Show show = findShow(showId);

        if (!show.getStatus().canMoveTo(newStatus)) {
            throw new BusinessException(
                    "Show cannot move from " + show.getStatus() + " to " + newStatus
            );
        }

        if (newStatus == ShowStatus.OPEN
                && show.getEvent() != null
                && show.getEvent().getStatus() != com.tickethub.event.enums.EventStatus.PUBLISHED) {
            throw new BusinessException(
                    "Cannot open a show for a non-published event."
            );
        }

        show.setStatus(newStatus);

        log.info("Show {} moved to {}", showId, newStatus);

        return showMapper.toResponse(show);
    }

    // =========================
    // DELETE SHOW
    // =========================

    @Override
    @Transactional
    public void delete(Long showId) {

        log.info("Deleting show with id={}", showId);

        Show show = findShow(showId);

        if (show.getStatus() == ShowStatus.OPEN
                || show.getStatus() == ShowStatus.SOLD_OUT
                || showSeatRepository.existsByShowShowIdAndStatus(showId, ShowSeatStatus.BOOKED)) {
            throw new BusinessException(
                    "Cannot delete show that is open or has booked seats."
            );
        }

        showSeatRepository.deleteByShowShowId(showId);
        showRepository.delete(show);

        log.info("Show deleted successfully with id={}", showId);
    }

    private void validateShowDurationAgainstEvent(ShowRequest request, Event event) {
        if (event.getDuration() != null && event.getDuration() > 0) {
            long minutes = java.time.Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
            if (minutes < event.getDuration()) {
                throw new BusinessException(
                        "Show time window (" + minutes + " mins) is shorter than the event duration (" + event.getDuration() + " mins)."
                );
            }
        }
    }

    // =========================
    // VALIDATE TIME
    // =========================

    private void validateTime(ShowRequest request) {

        if (request.getStartTime() == null ||
                request.getEndTime() == null) {

            throw new BusinessException(
                    "Start time and end time are required."
            );
        }

        if (!request.getEndTime().isAfter(request.getStartTime())) {

            throw new BusinessException(
                    "endTime must be after startTime."
            );
        }
    }

    // =========================
    // CHECK SHOW OVERLAP
    // =========================

    private void checkOverlap(
            ShowRequest request,
            Long showId) {

        long count;

        if (showId == null) {

            // CREATE
            count = showRepository.countOverlappingShows(
                    request.getScreenId(),
                    request.getShowDate(),
                    request.getStartTime(),
                    request.getEndTime()
            );

        } else {

            // UPDATE
            count = showRepository.countOverlappingShowsForUpdate(
                    request.getScreenId(),
                    request.getShowDate(),
                    request.getStartTime(),
                    request.getEndTime(),
                    showId
            );
        }

        if (count > 0) {

            throw new BusinessException(
                    "Another show is already scheduled on this screen during the requested time."
            );
        }
    }

    // =========================
    // FIND SHOW
    // =========================

    private Show findShow(Long showId) {

        return showRepository.findById(showId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Show not found: " + showId
                        )
                );
    }

    // =========================
    // FIND EVENT
    // =========================

    private Event findEvent(Long eventId) {

        return eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Event not found: " + eventId
                        )
                );
    }

    // =========================
    // FIND SCREEN
    // =========================

    private Screen findScreen(Long screenId) {

        return screenRepository.findById(screenId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Screen not found: " + screenId
                        )
                );
    }
}
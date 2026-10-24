package com.tickethub.event.service.impl;

import com.tickethub.event.dto.request.ScreenRequest;
import com.tickethub.event.dto.response.PageResponse;
import com.tickethub.event.dto.response.ScreenResponse;
import com.tickethub.event.entity.Screen;
import com.tickethub.event.entity.Venue;
import com.tickethub.event.exception.BusinessException;
import com.tickethub.event.exception.ResourceNotFoundException;
import com.tickethub.event.mapper.ScreenMapper;
import com.tickethub.event.repository.ScreenRepository;
import com.tickethub.event.repository.SeatRepository;
import com.tickethub.event.repository.ShowRepository;
import com.tickethub.event.repository.VenueRepository;
import com.tickethub.event.service.ScreenService;
import com.tickethub.event.util.PageableValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScreenServiceImpl implements ScreenService {

    private final ScreenRepository screenRepository;
    private final VenueRepository venueRepository;
    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final ScreenMapper screenMapper;
    private final PageableValidator pageableValidator;

    private final Set<String> allowedSortFields = Set.of(
            "screenId",
            "screenName"
    );

    // =========================
    // CREATE SCREEN
    // =========================

    @Override
    @Transactional
    public ScreenResponse create(ScreenRequest request) {

        log.info(
                "Creating screen={} for venueId={}",
                request.getScreenName(),
                request.getVenueId()
        );

        Venue venue = findVenue(request.getVenueId());

        if (screenRepository
                .existsByVenueVenueIdAndScreenNameIgnoreCase(
                        request.getVenueId(),
                        request.getScreenName())) {

            throw new BusinessException(
                    "Screen name already exists in this venue."
            );
        }

        Screen screen = Screen.builder()
                .screenName(request.getScreenName())
                .venue(venue)
                .build();

        Screen savedScreen = screenRepository.save(screen);

        log.info(
                "Screen created successfully with id={}",
                savedScreen.getScreenId()
        );

        return screenMapper.toResponse(savedScreen);
    }

    // =========================
    // GET ALL SCREENS
    // =========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScreenResponse> getAll(
            Pageable pageable) {

        log.info("Fetching all screens");

        pageableValidator.validate(
                pageable,
                allowedSortFields
        );

        return PageResponse.from(
                screenRepository.findAll(pageable)
                        .map(screenMapper::toResponse)
        );
    }

    // =========================
    // GET SCREENS BY VENUE
    // =========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScreenResponse> getByVenue(
            Long venueId,
            Pageable pageable) {

        log.info(
                "Fetching screens for venueId={}",
                venueId
        );

        findVenue(venueId);

        pageableValidator.validate(
                pageable,
                allowedSortFields
        );

        return PageResponse.from(
                screenRepository
                        .findByVenueVenueId(venueId, pageable)
                        .map(screenMapper::toResponse)
        );
    }

    // =========================
    // GET SCREEN BY ID
    // =========================

    @Override
    @Transactional(readOnly = true)
    public ScreenResponse getById(Long screenId) {

        log.info(
                "Fetching screen with id={}",
                screenId
        );

        Screen screen = findScreen(screenId);

        return screenMapper.toResponse(screen);
    }

    // =========================
    // UPDATE SCREEN
    // =========================

    @Override
    @Transactional
    public ScreenResponse update(
            Long screenId,
            ScreenRequest request) {

        log.info(
                "Updating screen with id={}",
                screenId
        );

        Screen screen = findScreen(screenId);

        Venue venue = findVenue(request.getVenueId());

        /*
         * Check duplicate screen name only when
         * the name/venue combination is changing.
         */
        boolean nameChanged =
                !screen.getScreenName()
                        .equalsIgnoreCase(request.getScreenName());

        boolean venueChanged =
                !screen.getVenue().getVenueId()
                        .equals(request.getVenueId());

        if (venueChanged && showRepository.existsByScreenScreenId(screenId)) {
            throw new BusinessException(
                    "Cannot move screen to another venue while shows exist for this screen."
            );
        }

        if ((nameChanged || venueChanged)
                && screenRepository
                .existsByVenueVenueIdAndScreenNameIgnoreCase(
                        request.getVenueId(),
                        request.getScreenName())) {

            throw new BusinessException(
                    "Screen name already exists in this venue."
            );
        }

        screen.setScreenName(request.getScreenName());
        screen.setVenue(venue);

        log.info(
                "Screen updated successfully with id={}",
                screenId
        );

        return screenMapper.toResponse(screen);
    }

    // =========================
    // DELETE SCREEN
    // =========================

    @Override
    @Transactional
    public void delete(Long screenId) {

        log.info(
                "Deleting screen with id={}",
                screenId
        );

        Screen screen = findScreen(screenId);

        if (seatRepository.existsByScreenScreenId(screenId)) {

            throw new BusinessException(
                    "Cannot delete screen because seats exist."
            );
        }

        if (showRepository.existsByScreenScreenId(screenId)) {

            throw new BusinessException(
                    "Cannot delete screen because shows exist."
            );
        }

        screenRepository.delete(screen);

        log.info(
                "Screen deleted successfully with id={}",
                screenId
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

    // =========================
    // FIND VENUE
    // =========================

    private Venue findVenue(Long venueId) {

        return venueRepository.findById(venueId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Venue not found: " + venueId
                        )
                );
    }
}
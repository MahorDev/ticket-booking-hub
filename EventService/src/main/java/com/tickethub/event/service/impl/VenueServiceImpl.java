package com.tickethub.event.service.impl;

import com.tickethub.event.dto.request.VenueRequest;
import com.tickethub.event.dto.response.PageResponse;
import com.tickethub.event.dto.response.VenueResponse;
import com.tickethub.event.entity.Venue;
import com.tickethub.event.exception.BusinessException;
import com.tickethub.event.exception.ResourceNotFoundException;
import com.tickethub.event.mapper.VenueMapper;
import com.tickethub.event.repository.ScreenRepository;
import com.tickethub.event.repository.VenueRepository;
import com.tickethub.event.service.VenueService;
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
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;
    private final ScreenRepository screenRepository;
    private final PageableValidator pageableValidator;

    private final Set<String> allowedSortFields = Set.of(
            "venueId",
            "name",
            "city",
            "state",
            "country"
    );

    // =========================
    // CREATE VENUE
    // =========================

    @Override
    @Transactional
    public VenueResponse create(VenueRequest request) {

        log.info("Creating venue with name={}", request.getName());

        Venue venue = venueMapper.toEntity(request);

        Venue savedVenue = venueRepository.save(venue);

        log.info(
                "Venue created successfully with id={}",
                savedVenue.getVenueId()
        );

        return venueMapper.toResponse(savedVenue);
    }

    // =========================
    // GET ALL VENUES
    // =========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> getAll(Pageable pageable) {

        log.info("Fetching all venues");

        pageableValidator.validate(
                pageable,
                allowedSortFields
        );

        return PageResponse.from(
                venueRepository.findAll(pageable)
                        .map(venueMapper::toResponse)
        );
    }

    // =========================
    // GET VENUE BY ID
    // =========================

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getById(Long venueId) {

        log.info("Fetching venue with id={}", venueId);

        Venue venue = findVenue(venueId);

        return venueMapper.toResponse(venue);
    }

    // =========================
    // UPDATE VENUE
    // =========================

    @Override
    @Transactional
    public VenueResponse update(
            Long venueId,
            VenueRequest request) {

        log.info("Updating venue with id={}", venueId);

        Venue venue = findVenue(venueId);

        venueMapper.update(venue, request);

        log.info(
                "Venue updated successfully with id={}",
                venueId
        );

        return venueMapper.toResponse(venue);
    }

    // =========================
    // DELETE VENUE
    // =========================

    @Override
    @Transactional
    public void delete(Long venueId) {

        log.info("Deleting venue with id={}", venueId);

        Venue venue = findVenue(venueId);

        if (screenRepository.existsByVenueVenueId(venueId)) {

            throw new BusinessException(
                    "Cannot delete venue because screens exist."
            );
        }

        venueRepository.delete(venue);

        log.info(
                "Venue deleted successfully with id={}",
                venueId
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
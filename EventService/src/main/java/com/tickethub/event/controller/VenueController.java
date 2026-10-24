package com.tickethub.event.controller;

import com.tickethub.event.dto.request.VenueRequest;
import com.tickethub.event.dto.response.*;
import com.tickethub.event.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@Tag(name = "Venues")
public class VenueController {

    private final VenueService service;

    private final ScreenService screenService;

    @PostMapping
    @Operation(summary = "Create venue")
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody VenueRequest r) {
        VenueResponse c = service.create(r);
        URI u = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(c.getVenueId()).toUri();
        return ResponseEntity.created(u).body(c);
    }

    @GetMapping
    @Operation(summary = "Get venues with pagination and sorting")
    public PageResponse<VenueResponse> getAll(@ParameterObject @PageableDefault(size = 20, sort = "venueId") Pageable p) {
        return service.getAll(p);
    }

    @GetMapping("/{venueId}")
    @Operation(summary = "Get venue by id")
    public VenueResponse getById(@PathVariable Long venueId) {
        return service.getById(venueId);
    }

    @GetMapping("/{venueId}/screens")
    @Operation(summary = "Get screens for venue")
    public PageResponse<ScreenResponse> getScreens(@PathVariable Long venueId, @ParameterObject @PageableDefault(size = 20, sort = "screenId") Pageable p) {
        return screenService.getByVenue(venueId, p);
    }

    @PutMapping("/{venueId}")
    @Operation(summary = "Update venue")
    public VenueResponse update(@PathVariable Long venueId, @Valid @RequestBody VenueRequest r) {
        return service.update(venueId, r);
    }

    @DeleteMapping("/{venueId}")
    @Operation(summary = "Delete venue")
    public ResponseEntity<Void> delete(@PathVariable Long venueId) {
        service.delete(venueId);
        return ResponseEntity.noContent().build();
    }
}

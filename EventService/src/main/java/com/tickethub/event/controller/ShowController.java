package com.tickethub.event.controller;

import com.tickethub.event.dto.request.ShowRequest;
import com.tickethub.event.dto.request.ShowStatusRequest;
import com.tickethub.event.dto.response.*;
import com.tickethub.event.service.ShowService;
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
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
@Tag(name = "Shows")
public class ShowController {

    private final ShowService service;

    private final com.tickethub.event.service.ShowSeatService showSeatService;

    @PostMapping
    @Operation(summary = "Create show")
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody ShowRequest r) {
        ShowResponse c = service.create(r);
        URI u = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(c.getShowId()).toUri();
        return ResponseEntity.created(u).body(c);
    }

    @GetMapping
    @Operation(summary = "Get shows with pagination and sorting")
    public PageResponse<ShowResponse> getAll(@ParameterObject @PageableDefault(size = 20, sort = "showDate") Pageable p) {
        return service.getAll(p);
    }

    @GetMapping("/{showId}/seats")
    @Operation(summary = "Get all seats for a show")
    public List<com.tickethub.event.dto.response.ShowSeatResponse> getSeats(@PathVariable Long showId) {
        return showSeatService.getAllByShow(showId);
    }

    @GetMapping("/{showId}/seats/page")
    @Operation(summary = "Get seats for show with pagination")
    public PageResponse<com.tickethub.event.dto.response.ShowSeatResponse> getSeatsPage(@PathVariable Long showId, @ParameterObject @PageableDefault(size = 50, sort = "showSeatId") Pageable p) {
        return showSeatService.getByShow(showId, p);
    }

    @GetMapping("/{showId}")
    @Operation(summary = "Get show by id")
    public ShowResponse getById(@PathVariable Long showId) {
        return service.getById(showId);
    }

    @PutMapping("/{showId}")
    @Operation(summary = "Update show")
    public ShowResponse update(@PathVariable Long showId, @Valid @RequestBody ShowRequest r) {
        return service.update(showId, r);
    }

    @PatchMapping("/{showId}/status")
    @Operation(summary = "Change show status (SCHEDULED -> OPEN -> SOLD_OUT / CANCELLED / COMPLETED)")
    public ShowResponse changeStatus(@PathVariable Long showId, @Valid @RequestBody ShowStatusRequest r) {
        return service.changeStatus(showId, r.getStatus());
    }

    @DeleteMapping("/{showId}")
    @Operation(summary = "Delete show")
    public ResponseEntity<Void> delete(@PathVariable Long showId) {
        service.delete(showId);
        return ResponseEntity.noContent().build();
    }
}

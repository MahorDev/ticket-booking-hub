package com.tickethub.event.controller;

import com.tickethub.event.dto.request.ScreenRequest;
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
@RequestMapping("/api/screens")
@RequiredArgsConstructor
@Tag(name = "Screens")
public class ScreenController {

    private final ScreenService service;

    private final SeatService seatService;

    private final ShowService showService;

    @PostMapping
    @Operation(summary = "Create screen")
    public ResponseEntity<ScreenResponse> create(@Valid @RequestBody ScreenRequest r) {
        ScreenResponse c = service.create(r);
        URI u = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(c.getScreenId()).toUri();
        return ResponseEntity.created(u).body(c);
    }

    @GetMapping
    @Operation(summary = "Get screens with pagination and sorting")
    public PageResponse<ScreenResponse> getAll(@ParameterObject @PageableDefault(size = 20, sort = "screenId") Pageable p) {
        return service.getAll(p);
    }

    @GetMapping("/{screenId}")
    @Operation(summary = "Get screen by id")
    public ScreenResponse getById(@PathVariable Long screenId) {
        return service.getById(screenId);
    }

    @GetMapping("/{screenId}/seats")
    @Operation(summary = "Get seats for screen")
    public PageResponse<SeatResponse> getSeats(@PathVariable Long screenId, @ParameterObject @PageableDefault(size = 50, sort = "seatId") Pageable p) {
        return seatService.getByScreen(screenId, p);
    }

    @GetMapping("/{screenId}/shows")
    @Operation(summary = "Get shows for screen")
    public PageResponse<ShowResponse> getShows(@PathVariable Long screenId, @ParameterObject @PageableDefault(size = 20, sort = "showDate") Pageable p) {
        return showService.getByScreen(screenId, p);
    }

    @PutMapping("/{screenId}")
    @Operation(summary = "Update screen")
    public ScreenResponse update(@PathVariable Long screenId, @Valid @RequestBody ScreenRequest r) {
        return service.update(screenId, r);
    }

    @DeleteMapping("/{screenId}")
    @Operation(summary = "Delete screen")
    public ResponseEntity<Void> delete(@PathVariable Long screenId) {
        service.delete(screenId);
        return ResponseEntity.noContent().build();
    }
}

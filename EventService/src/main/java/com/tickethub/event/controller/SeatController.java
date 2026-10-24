package com.tickethub.event.controller;

import com.tickethub.event.dto.request.SeatRequest;
import com.tickethub.event.dto.response.*;
import com.tickethub.event.service.SeatService;
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
@RequestMapping("/api/seats")
@RequiredArgsConstructor
@Tag(name = "Seats")
public class SeatController {

    private final SeatService service;

    @PostMapping
    @Operation(summary = "Create seat")
    public ResponseEntity<SeatResponse> create(@Valid @RequestBody SeatRequest r) {
        SeatResponse c = service.create(r);
        URI u = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(c.getSeatId()).toUri();
        return ResponseEntity.created(u).body(c);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Create seats in bulk")
    public ResponseEntity<List<SeatResponse>> createBulk(@Valid @RequestBody List<SeatRequest> requests) {
        List<SeatResponse> created = service.createBulk(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get seats with pagination and sorting")
    public PageResponse<SeatResponse> getAll(@ParameterObject @PageableDefault(size = 20, sort = "seatId") Pageable p) {
        return service.getAll(p);
    }

    @GetMapping("/{seatId}")
    @Operation(summary = "Get seat by id")
    public SeatResponse getById(@PathVariable Long seatId) {
        return service.getById(seatId);
    }

    @PutMapping("/{seatId}")
    @Operation(summary = "Update seat")
    public SeatResponse update(@PathVariable Long seatId, @Valid @RequestBody SeatRequest r) {
        return service.update(seatId, r);
    }

    @DeleteMapping("/{seatId}")
    @Operation(summary = "Delete seat")
    public ResponseEntity<Void> delete(@PathVariable Long seatId) {
        service.delete(seatId);
        return ResponseEntity.noContent().build();
    }
}

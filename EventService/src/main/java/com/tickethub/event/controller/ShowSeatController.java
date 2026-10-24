package com.tickethub.event.controller;

import com.tickethub.event.dto.request.ShowSeatRequest;
import com.tickethub.event.dto.response.*;
import com.tickethub.event.service.ShowSeatService;
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
@RequestMapping("/api/show-seats")
@RequiredArgsConstructor
@Tag(name = "Show Seats")
public class ShowSeatController {

    private final ShowSeatService service;

    @PostMapping
    @Operation(summary = "Create show seat")
    public ResponseEntity<ShowSeatResponse> create(@Valid @RequestBody ShowSeatRequest r) {
        ShowSeatResponse c = service.create(r);
        URI u = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(c.getShowSeatId()).toUri();
        return ResponseEntity.created(u).body(c);
    }

    @GetMapping("/{showSeatId}")
    @Operation(summary = "Get show seat by id")
    public ShowSeatResponse getById(@PathVariable Long showSeatId) {
        return service.getById(showSeatId);
    }

    @PutMapping("/{showSeatId}")
    @Operation(summary = "Update show seat")
    public ShowSeatResponse update(@PathVariable Long showSeatId, @Valid @RequestBody ShowSeatRequest r) {
        return service.update(showSeatId, r);
    }

    @DeleteMapping("/{showSeatId}")
    @Operation(summary = "Delete show seat")
    public ResponseEntity<Void> delete(@PathVariable Long showSeatId) {
        service.delete(showSeatId);
        return ResponseEntity.noContent().build();
    }
}

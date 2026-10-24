package com.tickethub.event.controller;

import com.tickethub.event.dto.request.EventRequest;
import com.tickethub.event.dto.request.EventStatusRequest;
import com.tickethub.event.dto.response.*;
import com.tickethub.event.enums.*;
import com.tickethub.event.service.EventService;
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
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events")
public class EventController {

    private final EventService service;
    private final com.tickethub.event.service.ShowService showService;
    private final com.tickethub.event.service.StorageService storageService;
    private final com.tickethub.event.repository.EventRepository eventRepository;

    @PostMapping(value = "/{eventId}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload event poster image to S3 media storage")
    public ResponseEntity<java.util.Map<String, String>> uploadPoster(
            @PathVariable Long eventId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new com.tickethub.event.exception.BusinessException("Uploaded file cannot be empty.");
        }
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new com.tickethub.event.exception.ResourceNotFoundException("Event not found: " + eventId));
        String filename = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename()
                : "upload.bin";
        String url = storageService.upload("events/" + eventId, filename, file.getBytes(), file.getContentType());
        event.setPosterUrl(url);
        eventRepository.save(event);
        return ResponseEntity.ok(java.util.Map.of("eventId", String.valueOf(eventId), "posterUrl", url));
    }

    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload media asset to S3")
    public ResponseEntity<java.util.Map<String, String>> uploadMedia(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new com.tickethub.event.exception.BusinessException("Uploaded file cannot be empty.");
        }
        String filename = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename()
                : "upload.bin";
        String url = storageService.upload(folder, filename, file.getBytes(), file.getContentType());
        return ResponseEntity.ok(java.util.Map.of("url", url, "filename", filename));
    }

    @PostMapping
    @Operation(summary = "Create event")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest r) {
        log.info("POST /api/events");
        EventResponse c = service.create(r);
        URI u = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(c.getEventId())
                .toUri();
        return ResponseEntity.created(u).body(c);
    }

    @GetMapping
    @Operation(summary = "Get events with pagination, sorting and filtering")
    public PageResponse<EventResponse> getAll(@ParameterObject @PageableDefault(size = 20, sort = "eventId") Pageable p, @RequestParam(required = false) String title, @RequestParam(required = false) EventType eventType, @RequestParam(required = false) String genre, @RequestParam(required = false) String language, @RequestParam(required = false) EventStatus status, @RequestHeader(value = "X-Authenticated-Role", required = false) String role) {
        log.info("GET /api/events");
        return service.getAll(p, title, eventType, genre, language, status, !isStaff(role));
    }

    @GetMapping("/{eventId}/shows")
    @Operation(summary = "Get shows for event")
    public PageResponse<com.tickethub.event.dto.response.ShowResponse> getShows(
            @PathVariable Long eventId,
            @ParameterObject @PageableDefault(size = 20, sort = "showDate") Pageable p,
            @RequestHeader(value = "X-Authenticated-Role", required = false) String role) {
        return showService.getByEvent(eventId, p, !isStaff(role));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get event by id")
    public EventResponse getById(@PathVariable Long eventId, @RequestHeader(value = "X-Authenticated-Role", required = false) String role) {
        return service.getById(eventId, !isStaff(role));
    }

    @PatchMapping("/{eventId}/status")
    @Operation(summary = "Change event status (DRAFT -> PUBLISHED -> CANCELLED)")
    public EventResponse changeStatus(@PathVariable Long eventId, @Valid @RequestBody EventStatusRequest r) {
        return service.changeStatus(eventId, r.getStatus());
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "Update event")
    public EventResponse update(@PathVariable Long eventId, @Valid @RequestBody EventRequest r) {
        return service.update(eventId, r);
    }


    private boolean isStaff(String role) {
        return "ADMIN".equals(role) || "EVENT_MANAGER".equals(role);
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "Delete event")
    public ResponseEntity<Void> delete(@PathVariable Long eventId) {
        service.delete(eventId);
        return ResponseEntity.noContent().build();
    }
}

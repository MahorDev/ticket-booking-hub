package com.tickethub.event.service.impl;

import com.tickethub.event.dto.request.EventRequest;
import com.tickethub.event.dto.response.EventResponse;
import com.tickethub.event.dto.response.PageResponse;
import com.tickethub.event.entity.Event;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.EventType;
import com.tickethub.event.exception.BusinessException;
import com.tickethub.event.exception.ResourceNotFoundException;
import com.tickethub.event.mapper.EventMapper;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.ShowRepository;
import com.tickethub.event.service.EventService;
import com.tickethub.event.util.PageableValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

 private final EventRepository eventRepository;
 private final EventMapper eventMapper;
 private final ShowRepository showRepository;
 private final PageableValidator pageableValidator;

 private final Set<String> allowedSortFields = Set.of(
         "eventId",
         "title",
         "eventType",
         "language",
         "duration",
         "rating",
         "genre",
         "status"
 );

 // =========================
 // CREATE
 // =========================

 @Override
 @Transactional
 public EventResponse create(EventRequest request) {

  log.info("Creating event with title: {}", request.getTitle());

  Event event = eventMapper.toEntity(request);

  Event savedEvent = eventRepository.save(event);

  log.info("Event created successfully with id: {}", savedEvent.getEventId());

  return eventMapper.toResponse(savedEvent);
 }

 // =========================
 // GET ALL
 // =========================

 @Override
 @Transactional(readOnly = true)
 public PageResponse<EventResponse> getAll(
         Pageable pageable,
         String title,
         EventType eventType,
         String genre,
         String language,
         EventStatus status,
         boolean publishedOnly) {

  log.info(
          "Fetching events - title={}, eventType={}, genre={}, language={}, status={}",
          title,
          eventType,
          genre,
          language,
          status
  );

  // Validate requested sorting fields
  pageableValidator.validate(pageable, allowedSortFields);

  // Customers only ever see PUBLISHED events
  EventStatus statusFilter = publishedOnly ? EventStatus.PUBLISHED : status;

  Page<Event> eventPage = eventRepository.search(
          title,
          eventType,
          genre,
          language,
          statusFilter,
          pageable
  );

  return PageResponse.from(
          eventPage.map(eventMapper::toResponse)
  );
 }

 // =========================
 // GET BY ID
 // =========================

 @Override
 @Transactional(readOnly = true)
 public EventResponse getById(Long eventId, boolean publishedOnly) {

  log.info("Fetching event with id: {}", eventId);

  Event event = findEvent(eventId);

  // A customer must not know that a DRAFT/CANCELLED event exists
  if (publishedOnly && event.getStatus() != EventStatus.PUBLISHED) {
   throw new ResourceNotFoundException("Event not found: " + eventId);
  }

  return eventMapper.toResponse(event);
 }

 // =========================
 // UPDATE
 // =========================

 @Override
 @Transactional
 public EventResponse update(
         Long eventId,
         EventRequest request) {

  log.info("Updating event with id: {}", eventId);

  Event event = findEvent(eventId);

  eventMapper.update(event, request);

  /*
   * No save() is strictly required here because the entity
   * is managed inside the transaction.
   *
   * Hibernate dirty checking will detect the changes
   * and execute UPDATE automatically during commit.
   */

  return eventMapper.toResponse(event);
 }

 // =========================
 // CHANGE STATUS (DRAFT -> PUBLISHED -> CANCELLED)
 // =========================

  @Override
  @Transactional
  public EventResponse changeStatus(Long eventId, EventStatus newStatus) {

   Event event = findEvent(eventId);

   if (!event.getStatus().canMoveTo(newStatus)) {
    throw new BusinessException(
            "Event cannot move from " + event.getStatus() + " to " + newStatus
    );
   }

   event.setStatus(newStatus);

   if (newStatus == EventStatus.CANCELLED) {
    var shows = showRepository.findAllByEventEventId(eventId);
    if (shows != null) {
     for (com.tickethub.event.entity.Show show : shows) {
      if (show.getStatus() == com.tickethub.event.enums.ShowStatus.SCHEDULED
              || show.getStatus() == com.tickethub.event.enums.ShowStatus.OPEN
              || show.getStatus() == com.tickethub.event.enums.ShowStatus.SOLD_OUT) {
       show.setStatus(com.tickethub.event.enums.ShowStatus.CANCELLED);
      }
     }
    }
   }

   log.info("Event {} moved to {}", eventId, newStatus);

   return eventMapper.toResponse(event);
  }

 // =========================
 // DELETE
 // =========================

 @Override
 @Transactional
 public void delete(Long eventId) {

  log.info("Deleting event with id: {}", eventId);

  // First check whether event exists
  if (!eventRepository.existsById(eventId)) {
   throw new ResourceNotFoundException(
           "Event not found: " + eventId
   );
  }

  // Don't delete an event that already has shows
  if (showRepository.existsByEventEventId(eventId)) {

   throw new BusinessException(
           "Cannot delete event because shows exist for this event."
   );
  }

  eventRepository.deleteById(eventId);

  log.info("Event deleted successfully with id: {}", eventId);
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
}
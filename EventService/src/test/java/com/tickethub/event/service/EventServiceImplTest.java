package com.tickethub.event.service;

import com.tickethub.event.dto.request.EventRequest;
import com.tickethub.event.dto.response.EventResponse;
import com.tickethub.event.entity.Event;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.EventType;
import com.tickethub.event.exception.BusinessException;
import com.tickethub.event.exception.ResourceNotFoundException;
import com.tickethub.event.mapper.EventMapper;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.ShowRepository;
import com.tickethub.event.service.impl.EventServiceImpl;
import com.tickethub.event.util.PageableValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceImplTest {

    private EventRepository eventRepository;
    private ShowRepository showRepository;
    private EventServiceImpl service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        showRepository = mock(ShowRepository.class);
        service = new EventServiceImpl(eventRepository, new EventMapper(), showRepository, new PageableValidator());
    }

    private Event event(EventStatus status) {
        return Event.builder().eventId(1L).title("Leo").eventType(EventType.MOVIE)
                .language("Tamil").duration(160).status(status).build();
    }

    @Test
    void create_newEventAlwaysStartsAsDraft() {
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));

        EventRequest request = new EventRequest();
        request.setTitle("Leo");
        request.setEventType(EventType.MOVIE);
        request.setLanguage("Tamil");
        request.setDuration(160);

        EventResponse response = service.create(request);

        assertEquals(EventStatus.DRAFT, response.getStatus());
    }

    @Test
    void changeStatus_draftToPublished_works() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event(EventStatus.DRAFT)));

        EventResponse response = service.changeStatus(1L, EventStatus.PUBLISHED);

        assertEquals(EventStatus.PUBLISHED, response.getStatus());
    }

    @Test
    void changeStatus_publishedToCancelled_cascadesToScheduledAndOpenShows() {
        Event pubEvent = event(EventStatus.PUBLISHED);
        com.tickethub.event.entity.Show scheduledShow = com.tickethub.event.entity.Show.builder()
                .showId(10L).status(com.tickethub.event.enums.ShowStatus.SCHEDULED).build();
        com.tickethub.event.entity.Show openShow = com.tickethub.event.entity.Show.builder()
                .showId(11L).status(com.tickethub.event.enums.ShowStatus.OPEN).build();
        com.tickethub.event.entity.Show completedShow = com.tickethub.event.entity.Show.builder()
                .showId(12L).status(com.tickethub.event.enums.ShowStatus.COMPLETED).build();

        when(eventRepository.findById(1L)).thenReturn(Optional.of(pubEvent));
        when(showRepository.findAllByEventEventId(1L)).thenReturn(java.util.List.of(scheduledShow, openShow, completedShow));

        EventResponse response = service.changeStatus(1L, EventStatus.CANCELLED);

        assertEquals(EventStatus.CANCELLED, response.getStatus());
        assertEquals(com.tickethub.event.enums.ShowStatus.CANCELLED, scheduledShow.getStatus());
        assertEquals(com.tickethub.event.enums.ShowStatus.CANCELLED, openShow.getStatus());
        assertEquals(com.tickethub.event.enums.ShowStatus.COMPLETED, completedShow.getStatus());
    }

    @Test
    void changeStatus_draftToCancelled_isRejected() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event(EventStatus.DRAFT)));

        assertThrows(BusinessException.class, () -> service.changeStatus(1L, EventStatus.CANCELLED));
    }

    @Test
    void changeStatus_unknownEvent_isNotFound() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.changeStatus(99L, EventStatus.PUBLISHED));
    }

    @Test
    void getById_customerCannotSeeDraftEvent() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event(EventStatus.DRAFT)));

        assertThrows(ResourceNotFoundException.class, () -> service.getById(1L, true));
    }

    @Test
    void getById_staffCanSeeDraftEvent() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event(EventStatus.DRAFT)));

        assertEquals(EventStatus.DRAFT, service.getById(1L, false).getStatus());
    }
}

package com.tickethub.event.service;

import com.tickethub.event.dto.request.ShowRequest;
import com.tickethub.event.dto.response.ShowResponse;
import com.tickethub.event.entity.Event;
import com.tickethub.event.entity.Screen;
import com.tickethub.event.entity.Show;
import com.tickethub.event.entity.Seat;
import com.tickethub.event.enums.SeatType;
import com.tickethub.event.enums.SeatSection;
import com.tickethub.event.entity.Venue;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.ShowStatus;
import com.tickethub.event.exception.BusinessException;
import com.tickethub.event.mapper.ShowMapper;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.ScreenRepository;
import com.tickethub.event.repository.ShowRepository;
import com.tickethub.event.repository.ShowSeatRepository;
import com.tickethub.event.service.impl.ShowServiceImpl;
import com.tickethub.event.util.PageableValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShowServiceImplTest {

    private ShowRepository showRepository;
    private EventRepository eventRepository;
    private ScreenRepository screenRepository;
    private com.tickethub.event.repository.SeatRepository seatRepository;
    private ShowServiceImpl service;

    private ShowSeatRepository showSeatRepository;

    private final Event event = Event.builder().eventId(1L).title("Leo").status(EventStatus.PUBLISHED).build();
    private final Screen screen = Screen.builder().screenId(1L).screenName("Screen 1")
            .venue(Venue.builder().venueId(1L).name("PVR Grand").address("Anna Salai").city("Chennai").build()).build();

    @BeforeEach
    void setUp() {
        showRepository = mock(ShowRepository.class);
        eventRepository = mock(EventRepository.class);
        screenRepository = mock(ScreenRepository.class);
        seatRepository = mock(com.tickethub.event.repository.SeatRepository.class);
        showSeatRepository = mock(ShowSeatRepository.class);
        service = new ShowServiceImpl(showRepository, eventRepository, screenRepository,
                showSeatRepository, seatRepository, new ShowMapper(), new PageableValidator());
    }

    private ShowRequest request(LocalTime start, LocalTime end) {
        ShowRequest r = new ShowRequest();
        r.setEventId(1L);
        r.setScreenId(1L);
        r.setShowDate(LocalDate.of(2026, 12, 25));
        r.setStartTime(start);
        r.setEndTime(end);
        r.setTicketPrice(new BigDecimal("250.00"));
        r.setCurrency("INR");
        return r;
    }

    @Test
    void create_newShowStartsAsScheduledWithPrice() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(screenRepository.findById(1L)).thenReturn(Optional.of(screen));
        when(showRepository.save(any(Show.class))).thenAnswer(i -> i.getArgument(0));
        when(seatRepository.findAllByScreenScreenId(1L)).thenReturn(java.util.List.of(
                Seat.builder().seatId(101L).seatNumber("A1").rowName("A").seatType(SeatType.REGULAR).section(SeatSection.LOWER).build()
        ));

        ShowResponse response = service.create(request(LocalTime.of(18, 0), LocalTime.of(21, 0)));

        assertEquals(ShowStatus.SCHEDULED, response.getStatus());
        assertEquals(new BigDecimal("250.00"), response.getTicketPrice());
        assertEquals("INR", response.getCurrency());
        assertEquals("PVR Grand", response.getVenueName());
        assertEquals("Anna Salai, Chennai", response.getVenueAddress());
    }

    @Test
    void create_endTimeBeforeStartTime_isRejected() {
        assertThrows(BusinessException.class,
                () -> service.create(request(LocalTime.of(21, 0), LocalTime.of(18, 0))));
    }

    @Test
    void changeStatus_scheduledToOpen_works() {
        Show show = Show.builder().showId(5L).event(event).screen(screen).status(ShowStatus.SCHEDULED).build();
        when(showRepository.findById(5L)).thenReturn(Optional.of(show));

        assertEquals(ShowStatus.OPEN, service.changeStatus(5L, ShowStatus.OPEN).getStatus());
    }

    @Test
    void changeStatus_scheduledToOpen_forCancelledEvent_isRejected() {
        Event cancelledEvent = Event.builder().eventId(1L).title("Leo").status(EventStatus.CANCELLED).build();
        Show show = Show.builder().showId(5L).event(cancelledEvent).screen(screen).status(ShowStatus.SCHEDULED).build();
        when(showRepository.findById(5L)).thenReturn(Optional.of(show));

        assertThrows(BusinessException.class, () -> service.changeStatus(5L, ShowStatus.OPEN));
    }

    @Test
    void changeStatus_scheduledToSoldOut_isRejected() {
        Show show = Show.builder().showId(5L).event(event).screen(screen).status(ShowStatus.SCHEDULED).build();
        when(showRepository.findById(5L)).thenReturn(Optional.of(show));

        assertThrows(BusinessException.class, () -> service.changeStatus(5L, ShowStatus.SOLD_OUT));
    }

    @Test
    void delete_scheduledShowWithoutBookedSeats_deletesShowSeatsAndShow() {
        Show show = Show.builder().showId(5L).event(event).screen(screen).status(ShowStatus.SCHEDULED).build();
        when(showRepository.findById(5L)).thenReturn(Optional.of(show));
        when(showSeatRepository.existsByShowShowIdAndStatus(5L, com.tickethub.event.enums.ShowSeatStatus.BOOKED)).thenReturn(false);

        service.delete(5L);

        org.mockito.Mockito.verify(showSeatRepository).deleteByShowShowId(5L);
        org.mockito.Mockito.verify(showRepository).delete(show);
    }

    @Test
    void delete_openShow_isRejected() {
        Show show = Show.builder().showId(5L).event(event).screen(screen).status(ShowStatus.OPEN).build();
        when(showRepository.findById(5L)).thenReturn(Optional.of(show));

        assertThrows(BusinessException.class, () -> service.delete(5L));
    }
}

package com.tickethub.event.mapper;

import com.tickethub.event.dto.response.ShowResponse;
import com.tickethub.event.entity.Show;
import com.tickethub.event.entity.Venue;
import org.springframework.stereotype.Component;

@Component
public class ShowMapper {

    public ShowResponse toResponse(Show s) {
        Venue venue = s.getScreen() != null ? s.getScreen().getVenue() : null;
        String venueAddress = (venue != null && (venue.getAddress() != null || venue.getCity() != null))
                ? venue.getAddress() + ", " + venue.getCity()
                : null;
        return ShowResponse.builder()
                .showId(s.getShowId())
                .eventId(s.getEvent().getEventId())
                .eventTitle(s.getEvent().getTitle())
                .screenId(s.getScreen().getScreenId())
                .screenName(s.getScreen().getScreenName())
                .venueId(venue != null ? venue.getVenueId() : null)
                .venueName(venue != null ? venue.getName() : null)
                .venueAddress(venueAddress)
                .showDate(s.getShowDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .ticketPrice(s.getTicketPrice())
                .currency(s.getCurrency())
                .status(s.getStatus())
                .eventStatus(s.getEvent().getStatus())
                .build();
    }
}

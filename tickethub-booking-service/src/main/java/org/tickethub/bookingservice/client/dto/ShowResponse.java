package org.tickethub.bookingservice.client.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record ShowResponse(
    Long showId,
    Long eventId,
    String eventTitle,
    String eventStatus,
    Long screenId,
    String screenName,
    String venueName,
    String venueAddress,
    LocalDate showDate,
    LocalTime startTime,
    LocalTime endTime,
    ShowStatus status
) {
    public ShowResponse(
        Long showId,
        Long eventId,
        String eventTitle,
        Long screenId,
        String screenName,
        LocalDate showDate,
        LocalTime startTime,
        LocalTime endTime,
        ShowStatus status
    ) {
        this(showId, eventId, eventTitle, null, screenId, screenName, null, null, showDate, startTime, endTime, status);
    }
}

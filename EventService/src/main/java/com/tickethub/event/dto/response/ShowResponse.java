package com.tickethub.event.dto.response;

import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.ShowStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowResponse {

    private Long showId;
    private Long eventId;
    private String eventTitle;
    private Long screenId;
    private String screenName;
    private Long venueId;
    private String venueName;
    private String venueAddress;
    private LocalDate showDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal ticketPrice;
    private String currency;
    private ShowStatus status;
    private EventStatus eventStatus;
}

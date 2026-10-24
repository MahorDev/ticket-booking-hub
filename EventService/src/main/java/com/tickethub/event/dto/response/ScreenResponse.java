package com.tickethub.event.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenResponse {

    private Long screenId;
    private String screenName;
    private Long venueId;
    private String venueName;
}

package com.tickethub.event.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueResponse {

    private Long venueId;
    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
}

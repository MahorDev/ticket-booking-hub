package com.tickethub.event.mapper;

import com.tickethub.event.dto.response.ScreenResponse;
import com.tickethub.event.entity.Screen;
import org.springframework.stereotype.Component;

@Component
public class ScreenMapper {

    public ScreenResponse toResponse(Screen s) {
        return ScreenResponse.builder().screenId(s.getScreenId()).screenName(s.getScreenName()).venueId(s.getVenue().getVenueId()).venueName(s.getVenue().getName()).build();
    }
}

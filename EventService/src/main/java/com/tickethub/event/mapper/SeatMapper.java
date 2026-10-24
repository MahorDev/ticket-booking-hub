package com.tickethub.event.mapper;

import com.tickethub.event.dto.response.SeatResponse;
import com.tickethub.event.entity.Seat;
import org.springframework.stereotype.Component;

@Component
public class SeatMapper {

    public SeatResponse toResponse(Seat s) {
        return SeatResponse.builder().seatId(s.getSeatId()).seatNumber(s.getSeatNumber()).rowName(s.getRowName()).seatType(s.getSeatType()).section(s.getSection()).basePrice(s.getBasePrice()).screenId(s.getScreen().getScreenId()).screenName(s.getScreen().getScreenName()).build();
    }
}

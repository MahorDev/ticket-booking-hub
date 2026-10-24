package com.tickethub.event.mapper;

import com.tickethub.event.dto.response.ShowSeatResponse;
import com.tickethub.event.entity.ShowSeat;
import org.springframework.stereotype.Component;

@Component
public class ShowSeatMapper {

    public ShowSeatResponse toResponse(ShowSeat s) {
        return ShowSeatResponse.builder()
                .showSeatId(s.getShowSeatId())
                .showId(s.getShow().getShowId())
                .seatId(s.getSeat().getSeatId())
                .seatNumber(s.getSeat().getSeatNumber())
                .rowName(s.getSeat().getRowName())
                .seatType(s.getSeat().getSeatType())
                .price(s.getPrice())
                .finalPrice(s.getPrice())
                .currency(s.getShow().getCurrency())
                .categoryId(null)
                .categoryName(s.getSeat().getSection() != null ? s.getSeat().getSection().name() : null)
                .status(s.getStatus())
                .build();
    }
}

package com.tickethub.event.mapper;

import com.tickethub.event.dto.request.VenueRequest;
import com.tickethub.event.dto.response.VenueResponse;
import com.tickethub.event.entity.Venue;
import org.springframework.stereotype.Component;

@Component
public class VenueMapper {

    public Venue toEntity(VenueRequest r) {
        return Venue.builder().name(r.getName()).address(r.getAddress()).city(r.getCity()).state(r.getState()).country(r.getCountry()).build();
    }

    public void update(Venue e, VenueRequest r) {
        e.setName(r.getName());
        e.setAddress(r.getAddress());
        e.setCity(r.getCity());
        e.setState(r.getState());
        e.setCountry(r.getCountry());
    }

    public VenueResponse toResponse(Venue e) {
        return VenueResponse.builder().venueId(e.getVenueId()).name(e.getName()).address(e.getAddress()).city(e.getCity()).state(e.getState()).country(e.getCountry()).build();
    }
}

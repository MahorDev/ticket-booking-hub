package org.tickethub.bookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.tickethub.bookingservice.client.dto.ShowResponse;
import org.tickethub.bookingservice.client.dto.ShowSeatResponse;

import java.util.List;@FeignClient(
        name = "event-service"

)
// url = "${clients.event-service.url}"
public interface EventServiceClient {

    @GetMapping("/api/shows/{showId}")
    ShowResponse getShow(@PathVariable Long showId);

    @GetMapping("/api/shows/{showId}/seats")
    List<ShowSeatResponse> getShowSeats(@PathVariable Long showId);
}
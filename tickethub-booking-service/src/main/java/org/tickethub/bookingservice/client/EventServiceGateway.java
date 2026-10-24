package org.tickethub.bookingservice.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.tickethub.bookingservice.client.dto.ShowResponse;
import org.tickethub.bookingservice.client.dto.ShowSeatResponse;
import org.tickethub.bookingservice.exception.DependencyException;
import org.tickethub.bookingservice.exception.NotFoundException;
import java.util.List;

@Component
public class EventServiceGateway {
    private final EventServiceClient client;
    public EventServiceGateway(EventServiceClient client){this.client=client;}
    @CircuitBreaker(name="eventService", fallbackMethod="getShowFallback")
    @Retry(name="eventService")
    public ShowResponse getShow(Long showId) {
        try {
            ShowResponse show = client.getShow(showId);
            if (show == null) {
                throw new NotFoundException("Show not found: " + showId);
            }
            return show;
        } catch (FeignException.NotFound ex) {
            throw new NotFoundException("Show not found: " + showId);
        } catch (FeignException ex) {
            throw new DependencyException("Event Service is unavailable while retrieving show " + showId, ex);
        }
    }

    private ShowResponse getShowFallback(Long showId, Throwable ex) {
        if (ex instanceof NotFoundException notFound) {
            throw notFound;
        }
        if (ex instanceof FeignException.NotFound) {
            throw new NotFoundException("Show not found: " + showId);
        }
        throw new DependencyException("Event Service is unavailable while retrieving show " + showId, ex);
    }

    @CircuitBreaker(name="eventService", fallbackMethod="getShowSeatsFallback")
    @Retry(name="eventService")
    public List<ShowSeatResponse> getShowSeats(Long showId) {
        try {
            List<ShowSeatResponse> seats = client.getShowSeats(showId);
            if (seats == null) {
                throw new DependencyException("Event Service returned no seat data");
            }
            return seats;
        } catch (FeignException.NotFound ex) {
            throw new NotFoundException("Show not found: " + showId);
        } catch (FeignException ex) {
            throw new DependencyException("Event Service is unavailable while retrieving seats for show " + showId, ex);
        }
    }

    private List<ShowSeatResponse> getShowSeatsFallback(Long showId, Throwable ex) {
        if (ex instanceof NotFoundException notFound) {
            throw notFound;
        }
        if (ex instanceof FeignException.NotFound) {
            throw new NotFoundException("Show not found: " + showId);
        }
        if (ex instanceof DependencyException dep) {
            throw dep;
        }
        throw new DependencyException("Event Service is unavailable while retrieving seats for show " + showId, ex);
    }
}

package org.tickethub.paymentservice.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.tickethub.paymentservice.dto.BookingResponse;
import org.tickethub.paymentservice.dto.PaymentUpdateRequest;
import org.tickethub.paymentservice.exception.PaymentNotFoundException;

@Component
public class BookingServiceGateway {
    private final BookingClient client;

    public BookingServiceGateway(BookingClient client) {
        this.client = client;
    }

    @CircuitBreaker(name = "bookingService", fallbackMethod = "getBookingFallback")
    @Retry(name = "bookingService")
    public BookingResponse getBooking(Long bookingId) {
        var response = client.getBooking(bookingId);
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Booking Service returned no booking data");
        }
        return response.getData();
    }

    private BookingResponse getBookingFallback(Long bookingId, Throwable ex) {
        rethrowClientException(bookingId, ex);
        throw new RuntimeException("Booking Service is unavailable: " + ex.getMessage(), ex);
    }

    @CircuitBreaker(name = "bookingService", fallbackMethod = "fallback")
    @Retry(name = "bookingService")
    public BookingResponse markPaymentSuccess(Long bookingId, String paymentReference) {
        client.markPaymentSuccess(bookingId, new PaymentUpdateRequest(paymentReference));
        return BookingResponse.builder()
                .bookingId(bookingId)
                .status("CONFIRMED")
                .build();
    }

    @CircuitBreaker(name = "bookingService", fallbackMethod = "failureFallback")
    @Retry(name = "bookingService")
    public void markPaymentFailure(Long bookingId, String paymentReference) {
        client.markPaymentFailure(bookingId, new PaymentUpdateRequest(paymentReference));
    }

    private void failureFallback(Long bookingId, String paymentReference, Throwable ex) {
        rethrowClientException(bookingId, ex);
        throw new RuntimeException("Booking Service is unavailable while releasing failed-payment reservation: " + ex.getMessage(), ex);
    }

    private BookingResponse fallback(Long bookingId, String paymentReference, Throwable ex) {
        rethrowClientException(bookingId, ex);
        throw new RuntimeException("Booking Service is unavailable: " + ex.getMessage(), ex);
    }

    private void rethrowClientException(Long bookingId, Throwable ex) {
        if (ex instanceof PaymentNotFoundException pnf) {
            throw pnf;
        }
        if (ex instanceof FeignException.NotFound) {
            throw new PaymentNotFoundException("Booking not found: " + bookingId);
        }
        if (ex instanceof FeignException.Conflict) {
            throw new IllegalStateException("Booking state conflict for booking " + bookingId + ": " + ex.getMessage(), ex);
        }
        if (ex instanceof FeignException.BadRequest) {
            throw new IllegalArgumentException("Invalid booking request for booking " + bookingId + ": " + ex.getMessage(), ex);
        }
        if (ex instanceof IllegalArgumentException iae) {
            throw iae;
        }
        if (ex instanceof IllegalStateException ise) {
            throw ise;
        }
    }
}

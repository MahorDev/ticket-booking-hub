package org.tickethub.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.tickethub.paymentservice.dto.BookingResponse;
import org.tickethub.paymentservice.dto.BookingApiResponse;
import org.tickethub.paymentservice.dto.PaymentUpdateRequest;

@FeignClient(name="booking-service")
//, url="${clients.booking-service.url}"
public interface BookingClient {
    @GetMapping("/api/bookings/{bookingId}") BookingApiResponse<BookingResponse> getBooking(@PathVariable Long bookingId);
    @PostMapping("/api/internal/payments/{bookingId}/success") void markPaymentSuccess(@PathVariable Long bookingId, @RequestBody PaymentUpdateRequest request);
    @PostMapping("/api/internal/payments/{bookingId}/failure") void markPaymentFailure(@PathVariable Long bookingId, @RequestBody PaymentUpdateRequest request);
}

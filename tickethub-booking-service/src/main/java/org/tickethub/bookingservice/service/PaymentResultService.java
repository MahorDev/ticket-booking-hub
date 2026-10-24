package org.tickethub.bookingservice.service;

import org.tickethub.bookingservice.dto.response.BookingResponse;

public interface PaymentResultService {
    BookingResponse markSuccess(Long bookingId, String paymentReference);
    BookingResponse markFailure(Long bookingId, String paymentReference);
}

package org.tickethub.paymentservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String BOOKING_HEADER = "X-Booking-ID";
    private static final String DELAY_HEADER = "X-Simulate-Delay";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || !correlationId.matches("[A-Za-z0-9._-]{1,128}")) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        String bookingId = request.getHeader(BOOKING_HEADER);
        if (bookingId != null && !bookingId.isBlank()) {
            MDC.put("bookingId", bookingId.trim());
            response.setHeader(BOOKING_HEADER, bookingId.trim());
        }

        // Observability Baseline: Simulate artificial delay to verify metrics & tracing
        String delayStr = request.getHeader(DELAY_HEADER);
        if (delayStr != null && delayStr.matches("\\d+")) {
            try {
                long delayMs = Long.parseLong(delayStr);
                log.warn("Observability Drill: Simulating artificial payment delay of {}ms for bookingId={}", delayMs, bookingId);
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }
}

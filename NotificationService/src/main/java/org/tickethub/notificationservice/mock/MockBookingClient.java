package org.tickethub.notificationservice.mock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fallback adapter when an event does not carry the customer's email address.
 */
@Component
public class MockBookingClient {
    @Value("${notification.default-recipient:customer@tickethub.com}")
    private String defaultRecipient;

    public String getUserEmail(Long bookingId) {
        if (defaultRecipient == null || defaultRecipient.isBlank()) {
            return "customer@tickethub.com";
        }
        return defaultRecipient;
    }
}

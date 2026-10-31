package org.tickethub.notificationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario 13: Schema Evolution & Contract Backward/Forward Compatibility.
 * Verifies that consumers handle additive fields, renamed fields (aliases),
 * and completely unknown future fields without breaking.
 */
class EventSchemaEvolutionTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Task 1: Should deserialize Legacy V1 Payload using 'amount' and no seat category")
    void shouldDeserializeLegacyV1Payload() throws Exception {
        String v1Payload = """
            {
                "eventMessageId": "evt-v1-1001",
                "eventType": "BookingConfirmed",
                "bookingId": 1001,
                "recipientEmail": "v1@tickethub.com",
                "amount": 350.00,
                "currency": "INR"
            }
            """;

        BookingConfirmedEvent event = objectMapper.readValue(v1Payload, BookingConfirmedEvent.class);
        assertNotNull(event);
        assertEquals(1001L, event.getBookingId());
        assertEquals("v1@tickethub.com", event.getRecipientEmail());
        assertEquals(new BigDecimal("350.00"), event.getTotalAmount());
        assertNull(event.getSeatCategory());
    }

    @Test
    @DisplayName("Task 2: Should deserialize Modern V2 Payload with 'totalAmount' and 'seatCategory'")
    void shouldDeserializeModernV2Payload() throws Exception {
        String v2Payload = """
            {
                "eventMessageId": "evt-v2-2002",
                "eventType": "BookingConfirmed",
                "bookingId": 2002,
                "recipientEmail": "v2@tickethub.com",
                "totalAmount": 550.00,
                "currency": "INR",
                "seatCategory": "VIP_RECLINER"
            }
            """;

        BookingConfirmedEvent event = objectMapper.readValue(v2Payload, BookingConfirmedEvent.class);
        assertNotNull(event);
        assertEquals(2002L, event.getBookingId());
        assertEquals("VIP_RECLINER", event.getSeatCategory());
        assertEquals(new BigDecimal("550.00"), event.getTotalAmount());
    }

    @Test
    @DisplayName("Task 3: Should ignore future unknown fields gracefully (Forward Compatibility)")
    void shouldIgnoreFutureUnknownFields() throws Exception {
        String futureV3Payload = """
            {
                "eventMessageId": "evt-v3-3003",
                "eventType": "BookingConfirmed",
                "bookingId": 3003,
                "recipientEmail": "v3@tickethub.com",
                "totalAmount": 750.00,
                "currency": "INR",
                "seatCategory": "IMAX_BALCONY",
                "loyaltyBonusPoints": 120,
                "promotionCode": "SUMMER_SALE",
                "experimentalMetadata": {"geo": "IN-KA", "device": "iOS"}
            }
            """;

        assertDoesNotThrow(() -> {
            BookingConfirmedEvent event = objectMapper.readValue(futureV3Payload, BookingConfirmedEvent.class);
            assertEquals(3003L, event.getBookingId());
            assertEquals("IMAX_BALCONY", event.getSeatCategory());
        });
    }
}

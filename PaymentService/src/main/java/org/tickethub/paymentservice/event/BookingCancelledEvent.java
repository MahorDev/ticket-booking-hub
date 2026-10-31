package org.tickethub.paymentservice.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingCancelledEvent {

    @Builder.Default
    @JsonAlias({"eventMessageId", "eventId", "messageId"})
    private String eventMessageId = java.util.UUID.randomUUID().toString();

    @Builder.Default
    private String eventType = "BookingCancelled";

    private Long bookingId;

    private Long userId;

    private String reason;

    @Builder.Default
    private Long aggregateVersion = 2L;

    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}

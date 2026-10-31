package org.tickethub.bookingservice.event;

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
public class PaymentFailedEvent {

    @Builder.Default
    @JsonAlias({"eventMessageId", "eventId", "messageId"})
    private String eventMessageId = java.util.UUID.randomUUID().toString();

    @Builder.Default
    private String eventType = "PaymentFailed";

    private Long bookingId;

    private Long paymentId;

    private String reason;

    @Builder.Default
    private Long aggregateVersion = 1L;

    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
}

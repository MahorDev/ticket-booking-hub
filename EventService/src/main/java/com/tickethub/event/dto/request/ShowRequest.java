package com.tickethub.event.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.*;

@Getter
@Setter
public class ShowRequest {

    @NotNull
    private Long eventId;

    @NotNull
    private Long screenId;

    @NotNull
    private LocalDate showDate;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    @Positive
    private BigDecimal ticketPrice;

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter code such as INR")
    private String currency;
}

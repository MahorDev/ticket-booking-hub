package com.tickethub.event.dto.request;

import com.tickethub.event.enums.EventType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventRequest {

    @NotBlank
    @Size(max = 150)
    private String title;

    @Size(max = 1000)
    private String description;

    @NotNull
    private EventType eventType;

    @NotBlank
    @Size(max = 50)
    private String language;

    @NotNull
    @Positive
    private Integer duration;

    @Size(max = 20)
    private String rating;

    @Size(max = 100)
    private String genre;
}

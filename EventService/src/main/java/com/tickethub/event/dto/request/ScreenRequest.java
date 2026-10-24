package com.tickethub.event.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScreenRequest {

    @NotBlank
    @Size(max = 100)
    private String screenName;

    @NotNull
    private Long venueId;
}

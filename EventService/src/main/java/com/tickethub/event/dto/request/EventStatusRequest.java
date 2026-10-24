package com.tickethub.event.dto.request;

import com.tickethub.event.enums.EventStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventStatusRequest {

    @NotNull
    private EventStatus status;
}

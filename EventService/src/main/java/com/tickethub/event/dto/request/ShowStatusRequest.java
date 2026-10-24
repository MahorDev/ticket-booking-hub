package com.tickethub.event.dto.request;

import com.tickethub.event.enums.ShowStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShowStatusRequest {

    @NotNull
    private ShowStatus status;
}

package com.tickethub.event.exception;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class ApiError {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String,String> validationErrors;
}

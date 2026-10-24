package com.tickethub.event.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * One place that turns every exception into the same JSON error body (ApiError).
 * 404 = not found, 409 = business rule broken, 400 = bad input, 500 = unexpected.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException e, HttpServletRequest r) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), r, null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e, HttpServletRequest r) {
        return build(HttpStatus.CONFLICT, e.getMessage(), r, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(DataIntegrityViolationException e, HttpServletRequest r) {
        log.warn("Data integrity violation on {} {}: {}", r.getMethod(), r.getRequestURI(), e.getMessage());
        return build(HttpStatus.CONFLICT, "Data integrity violation: duplicate or conflicting record", r, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        var errors = e.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(x -> x.getField(), x -> x.getDefaultMessage(), (a, b) -> a));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", r, errors);
    }

    // Broken JSON, or a value that is not valid for an enum / date / number (e.g. status=ACTIVE)
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badInput(Exception e, HttpServletRequest r) {
        log.warn("Bad input on {} {}: {}", r.getMethod(), r.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Invalid request: malformed JSON or an invalid value", r, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception e, HttpServletRequest r) {
        // Spring's own web errors (wrong URL = 404, wrong method = 405 ...) keep their real status
        if (e instanceof ErrorResponse er) {
            return build(HttpStatus.valueOf(er.getStatusCode().value()), er.getBody().getDetail(), r, null);
        }
        log.error("Unexpected error on {} {}", r.getMethod(), r.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", r, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus s, String m, HttpServletRequest r, java.util.Map<String, String> v) {
        return ResponseEntity.status(s).body(ApiError.builder().timestamp(LocalDateTime.now()).status(s.value()).error(s.getReasonPhrase()).message(m).path(r.getRequestURI()).validationErrors(v).build());
    }
}

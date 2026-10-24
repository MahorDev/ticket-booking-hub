package com.tickethub.user.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException e, HttpServletRequest r) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), r, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateResourceException e, HttpServletRequest r) {
        return build(HttpStatus.CONFLICT, e.getMessage(), r, null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> unauthorized(InvalidCredentialsException e, HttpServletRequest r) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage(), r, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> forbidden(ForbiddenException e, HttpServletRequest r) {
        return build(HttpStatus.FORBIDDEN, e.getMessage(), r, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException e, HttpServletRequest r) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), r, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        var errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        x -> x.getField(),
                        x -> x.getDefaultMessage() != null ? x.getDefaultMessage() : "Invalid value",
                        (a, b) -> a));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", r, errors);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> badInput(Exception e, HttpServletRequest r) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Invalid request", r, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception e, HttpServletRequest r) {
        if (e instanceof ErrorResponse er) {
            return build(HttpStatus.valueOf(er.getStatusCode().value()), er.getBody().getDetail(), r, null);
        }
        log.error("Unexpected error on {} {}", r.getMethod(), r.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", r, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus s, String m, HttpServletRequest r, Map<String, String> v) {
        return ResponseEntity.status(s).body(
                ApiError.builder()
                        .timestamp(LocalDateTime.now())
                        .status(s.value())
                        .error(s.getReasonPhrase())
                        .message(m)
                        .path(r.getRequestURI())
                        .validationErrors(v)
                        .build()
        );
    }
}

package org.tickethub.bookingservice.exception;
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}

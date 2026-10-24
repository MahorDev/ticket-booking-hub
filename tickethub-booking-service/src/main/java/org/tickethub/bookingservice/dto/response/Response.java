package org.tickethub.bookingservice.dto.response;

public record Response<T>(
        boolean error,
        String message,
        T data
) {
    public static <T> Response<T> success(String message, T data) {
        return new Response<>(false, message, data);
    }

    public static <T> Response<T> error(String message) {
        return new Response<>(true, message, null);
    }
}

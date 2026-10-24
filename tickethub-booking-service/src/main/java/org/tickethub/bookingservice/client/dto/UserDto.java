package org.tickethub.bookingservice.client.dto;

public record UserDto(
    Long userId,
    String email,
    String phone,
    String fullName,
    String role
) {}

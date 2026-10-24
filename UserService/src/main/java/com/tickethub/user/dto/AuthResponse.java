package com.tickethub.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private Long userId;
    private String email;
    private String phone;
    private String fullName;
    private String role;
    private String authProvider;
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn;
}

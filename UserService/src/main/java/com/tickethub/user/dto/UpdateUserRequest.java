package com.tickethub.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    private String fullName;

    @Pattern(regexp = "^[+]?[0-9\\-\\s]{7,20}$", message = "Phone number must be valid")
    private String phone;

    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;
}

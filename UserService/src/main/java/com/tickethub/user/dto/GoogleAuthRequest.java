package com.tickethub.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthRequest {

    private String idToken;
    private String code;
    private String email;
    private String fullName;
    private String phone;
    private String googleSub;
}

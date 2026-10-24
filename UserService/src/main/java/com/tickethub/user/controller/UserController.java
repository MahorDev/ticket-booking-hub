package com.tickethub.user.controller;

import com.tickethub.user.dto.*;
import com.tickethub.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping({"/api/users/register", "/auth/register"})
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping({"/api/users/login", "/auth/user-login"})
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping({"/api/users/auth/google", "/auth/google"})
    public ResponseEntity<AuthResponse> googleAuth(@RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(userService.googleAuth(request));
    }

    @GetMapping({"/api/users/auth/google/url", "/auth/google/url"})
    public ResponseEntity<Map<String, String>> googleAuthUrl() {
        return ResponseEntity.ok(userService.getGoogleAuthorizationUrl());
    }

    @GetMapping("/api/users/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Authenticated-Email", required = false) String emailHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(userService.getCurrentUser(userIdHeader, emailHeader, authHeader));
    }

    @GetMapping({"/api/users/{userId}", "/api/users/internal/{userId}"})
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @GetMapping("/api/users/by-email")
    public ResponseEntity<UserResponse> getUserByEmail(@RequestParam("email") String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @PutMapping("/api/users/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String callerUserId,
            @RequestHeader(value = "X-Authenticated-Email", required = false) String callerEmail,
            @RequestHeader(value = "X-Authenticated-Role", required = false) String callerRole,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(userService.updateUser(
                userId, request, callerUserId, callerEmail, callerRole, authHeader));
    }
}

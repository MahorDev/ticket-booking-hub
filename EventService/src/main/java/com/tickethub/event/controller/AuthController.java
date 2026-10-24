//package com.tickethub.event.controller;
//
//import com.tickethub.event.dto.request.LoginRequest;
//import com.tickethub.event.dto.response.LoginResponse;
//import com.tickethub.event.security.JwtService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/auth")
//@RequiredArgsConstructor
//@Tag(name = "Authentication")
//public class AuthController {
//
//    private final AuthenticationManager authenticationManager;
//
//    private final UserDetailsService users;
//
//    private final JwtService jwt;
//
//    @PostMapping("/login")
//    @Operation(summary = "Login and receive JWT")
//    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest r) {
//        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(r.getUsername(), r.getPassword()));
//        var u = users.loadUserByUsername(r.getUsername());
//        return ResponseEntity.ok(LoginResponse
//                .builder()
//                .token(jwt.generateToken(u))
//                .tokenType("Bearer")
//                .expiresIn(jwt.getExpirationMs())
//                .build());
//    }
//}

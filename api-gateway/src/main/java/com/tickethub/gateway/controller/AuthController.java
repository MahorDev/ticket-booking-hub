package com.tickethub.gateway.controller;

import com.tickethub.gateway.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
        RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.PATCH
})
public class AuthController {

    private final JwtService jwt;
    private final String adminUser, adminPass, managerUser, managerPass, customerUser, customerPass;


    public AuthController(JwtService jwt, @Value("${app.admin.username}") String au, @Value("${app.admin.password}") String ap, @Value("${app.manager.username}") String mu, @Value("${app.manager.password}") String mp, @Value("${app.customer.username}") String cu, @Value("${app.customer.password}") String cp) {

        this.jwt = jwt;
        adminUser = au;
        adminPass = ap;
        managerUser = mu;
        managerPass = mp;
        customerUser = cu;
        customerPass = cp;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest r) {
        String role = null;
        Long userId = null;
        String email = null;
        if (adminUser.equals(r.username()) && adminPass.equals(r.password())) {
            role = "ADMIN";
            userId = 1L;
            email = "admin@tickethub.com";
        } else if (managerUser.equals(r.username()) && managerPass.equals(r.password())) {
            role = "EVENT_MANAGER";
            userId = 2L;
            email = "manager@tickethub.com";
        } else if (customerUser.equals(r.username()) && customerPass.equals(r.password())) {
            role = "CUSTOMER";
            userId = 101L;
            email = "customer@tickethub.com";
        }
        if (role == null)
            return ResponseEntity.status(401).body(Map.of("error", true, "message", "Invalid username or password"));
        String token = jwt.generateToken(userId, r.username(), email, role);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "tokenType", "Bearer",
                "expiresIn", jwt.getExpirationMs(),
                "role", role,
                "userId", userId,
                "email", email));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {

    }
}

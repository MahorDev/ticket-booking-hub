package com.tickethub.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.user.dto.*;
import com.tickethub.user.entity.User;
import com.tickethub.user.enums.AuthProvider;
import com.tickethub.user.enums.UserRole;
import com.tickethub.user.exception.*;
import com.tickethub.user.repository.UserRepository;
import com.tickethub.user.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String googleClientId;
    private final String googleRedirectUri;
    private final boolean verifyGoogleToken;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${app.google.client-id:}") String googleClientId,
            @Value("${app.google.redirect-uri:http://localhost:8080/auth/google/callback}") String googleRedirectUri,
            @Value("${app.google.verify-token:false}") boolean verifyGoogleToken) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.googleClientId = googleClientId;
        this.googleRedirectUri = googleRedirectUri;
        this.verifyGoogleToken = verifyGoogleToken;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateResourceException("User already exists with email: " + normalizedEmail);
        }

        UserRole role = parseRole(request.getRole());
        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .role(role)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(UserLoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!Boolean.TRUE.equals(user.getEnabled())
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse googleAuth(GoogleAuthRequest request) {
        if (request == null) {
            throw new BadRequestException("Google auth request body is required");
        }

        String email = request.getEmail();
        String fullName = request.getFullName();
        String googleSub = request.getGoogleSub();
        String phone = request.getPhone();

        if (request.getIdToken() != null && !request.getIdToken().isBlank()) {
            String idToken = request.getIdToken().trim();
            if (verifyGoogleToken) {
                Map<String, Object> tokenInfo = verifyIdTokenWithGoogle(idToken);
                email = stringVal(tokenInfo.get("email"));
                if (fullName == null || fullName.isBlank()) {
                    fullName = stringVal(tokenInfo.get("name"));
                }
                googleSub = stringVal(tokenInfo.get("sub"));
            } else {
                Map<String, Object> decoded = tryDecodeJwtPayload(idToken);
                if (!decoded.isEmpty()) {
                    if (email == null || email.isBlank()) {
                        email = stringVal(decoded.get("email"));
                    }
                    if (fullName == null || fullName.isBlank()) {
                        fullName = stringVal(decoded.get("name"));
                    }
                    if (googleSub == null || googleSub.isBlank()) {
                        googleSub = stringVal(decoded.get("sub"));
                    }
                }
            }
        } else if (verifyGoogleToken) {
            throw new InvalidCredentialsException("Google idToken is required when token verification is enabled");
        }

        if (email == null || email.isBlank()) {
            throw new BadRequestException("Google authentication requires a valid email or idToken");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String resolvedSub = (googleSub != null && !googleSub.isBlank())
                ? googleSub.trim()
                : "google-" + normalizedEmail;

        Optional<User> existingOpt = userRepository.findByGoogleSub(resolvedSub);
        if (existingOpt.isEmpty()) {
            existingOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        }

        User user;
        if (existingOpt.isPresent()) {
            user = existingOpt.get();
            if (user.getGoogleSub() == null || user.getGoogleSub().isBlank()) {
                user.setGoogleSub(resolvedSub);
            }
            if (fullName != null && !fullName.isBlank()) {
                user.setFullName(fullName.trim());
            }
            if (phone != null && !phone.isBlank()) {
                user.setPhone(phone.trim());
            }
            user = userRepository.save(user);
        } else {
            user = User.builder()
                    .email(normalizedEmail)
                    .fullName(fullName != null && !fullName.isBlank() ? fullName.trim() : null)
                    .phone(phone != null && !phone.isBlank() ? phone.trim() : null)
                    .googleSub(resolvedSub)
                    .role(UserRole.CUSTOMER)
                    .authProvider(AuthProvider.GOOGLE)
                    .enabled(true)
                    .build();
            user = userRepository.save(user);
        }

        return buildAuthResponse(user);
    }

    public Map<String, String> getGoogleAuthorizationUrl() {
        String encodedRedirect = URLEncoder.encode(googleRedirectUri, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode("openid email profile", StandardCharsets.UTF_8);
        String clientIdParam = googleClientId != null ? googleClientId : "";
        String url = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code"
                + "&client_id=" + URLEncoder.encode(clientIdParam, StandardCharsets.UTF_8)
                + "&redirect_uri=" + encodedRedirect
                + "&scope=" + encodedScope
                + "&access_type=offline";
        return Map.of(
                "authorizationUrl", url,
                "clientId", clientIdParam,
                "redirectUri", googleRedirectUri
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email parameter is required");
        }
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userIdHeader, String emailHeader, String authHeader) {
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                Long id = Long.parseLong(userIdHeader.trim());
                Optional<User> byId = userRepository.findById(id);
                if (byId.isPresent()) {
                    return toUserResponse(byId.get());
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (emailHeader != null && !emailHeader.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmailIgnoreCase(emailHeader.trim());
            if (byEmail.isPresent()) {
                return toUserResponse(byEmail.get());
            }
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = jwtTokenService.parse(token);
                Object uidObj = claims.get("userId");
                if (uidObj != null) {
                    Long uid = Long.parseLong(uidObj.toString());
                    Optional<User> byId = userRepository.findById(uid);
                    if (byId.isPresent()) {
                        return toUserResponse(byId.get());
                    }
                }
                String claimEmail = claims.get("email", String.class);
                if (claimEmail == null || claimEmail.isBlank()) {
                    claimEmail = claims.getSubject();
                }
                if (claimEmail != null && !claimEmail.isBlank()) {
                    Optional<User> byEmail = userRepository.findByEmailIgnoreCase(claimEmail.trim());
                    if (byEmail.isPresent()) {
                        return toUserResponse(byEmail.get());
                    }
                }
            } catch (Exception e) {
                throw new InvalidCredentialsException("Invalid or expired token");
            }
        }

        throw new InvalidCredentialsException("Authenticated user context not found");
    }

    @Transactional
    public UserResponse updateUser(
            Long userId,
            UpdateUserRequest request,
            String callerUserId,
            String callerEmail,
            String callerRole,
            String authHeader) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        verifyOwnershipOrAdmin(user, callerUserId, callerEmail, callerRole, authHeader);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        User saved = userRepository.save(user);
        return toUserResponse(saved);
    }

    private void verifyOwnershipOrAdmin(
            User targetUser,
            String callerUserId,
            String callerEmail,
            String callerRole,
            String authHeader) {
        String effectiveRole = callerRole;
        String effectiveUserId = callerUserId;
        String effectiveEmail = callerEmail;

        if ((effectiveUserId == null || effectiveUserId.isBlank())
                && (effectiveEmail == null || effectiveEmail.isBlank())
                && (effectiveRole == null || effectiveRole.isBlank())
                && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtTokenService.parse(authHeader.substring(7).trim());
                if (claims.get("userId") != null) {
                    effectiveUserId = claims.get("userId").toString();
                }
                if (claims.get("email") != null) {
                    effectiveEmail = claims.get("email").toString();
                } else if (claims.getSubject() != null) {
                    effectiveEmail = claims.getSubject();
                }
                Object roles = claims.get("roles");
                if (roles instanceof Collection<?> col && !col.isEmpty()) {
                    effectiveRole = String.valueOf(col.iterator().next()).replace("ROLE_", "");
                }
            } catch (Exception e) {
                throw new InvalidCredentialsException("Invalid or expired token");
            }
        }

        if ("ADMIN".equalsIgnoreCase(effectiveRole)) {
            return;
        }
        if (effectiveUserId != null && !effectiveUserId.isBlank()) {
            try {
                if (targetUser.getUserId().equals(Long.parseLong(effectiveUserId.trim()))) {
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (effectiveEmail != null && !effectiveEmail.isBlank()
                && targetUser.getEmail().equalsIgnoreCase(effectiveEmail.trim())) {
            return;
        }

        throw new ForbiddenException("You are not authorized to update this user profile");
    }

    private Map<String, Object> verifyIdTokenWithGoogle(String idToken) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={idToken}", idToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null || body.get("email") == null) {
                throw new InvalidCredentialsException("Google ID token verification failed");
            }
            if (googleClientId != null && !googleClientId.isBlank()) {
                String aud = stringVal(body.get("aud"));
                if (!googleClientId.equals(aud)) {
                    throw new InvalidCredentialsException("Google ID token audience mismatch");
                }
            }
            return body;
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            throw new InvalidCredentialsException("Invalid Google ID token");
        }
    }

    private Map<String, Object> tryDecodeJwtPayload(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length >= 2) {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
                return objectMapper.readValue(decodedBytes, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.debug("Could not decode idToken as JWT payload: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    private String stringVal(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private UserRole parseRole(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return UserRole.CUSTOMER;
        }
        String normalized = roleStr.trim().toUpperCase(Locale.ROOT).replace("ROLE_", "");
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + roleStr);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenService.generateToken(user);
        return AuthResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .authProvider(user.getAuthProvider().name())
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpirationMs())
                .build();
    }

    public UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .authProvider(user.getAuthProvider().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

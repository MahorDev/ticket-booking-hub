package com.tickethub.gateway.security;

import com.tickethub.gateway.controller.AuthController;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtGatewaySecurityTest {

    private static final String SECRET = "please-set-a-strong-secret-of-at-least-32-bytes";
    private JwtService jwtService;
    private JwtGatewayFilter gatewayFilter;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600000L, "", "");
        gatewayFilter = new JwtGatewayFilter(jwtService);
        authController = new AuthController(
                jwtService,
                "admin", "admin123",
                "eventmanager", "manager123",
                "customer", "customer123");
    }

    @Test
    void shouldRejectUnsignedOrForgedOrMissingJwtTokenWith401() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker\",\"roles\":[\"ROLE_ADMIN\"],\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        String forgedToken = header + "." + payload + ".invalidsig";

        assertThatThrownBy(() -> jwtService.parse(forgedToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT token");

        // Missing token on protected endpoint -> 401
        MockServerWebExchange missingEx = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me").build());
        gatewayFilter.filter(missingEx, ex -> Mono.empty()).block();
        assertThat(missingEx.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Forged token on protected endpoint -> 401
        MockServerWebExchange forgedEx = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
                        .header("Authorization", "Bearer " + forgedToken)
                        .build());
        gatewayFilter.filter(forgedEx, ex -> Mono.empty()).block();
        assertThat(forgedEx.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAuthenticateAllRolesAndRejectBadCredentialsInAuthController() {
        // 1. Admin login -> 200 OK
        ResponseEntity<Map<String, Object>> adminResp = authController.login(
                new AuthController.LoginRequest("admin", "admin123"));
        assertThat(adminResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminResp.getBody()).containsEntry("userId", 1L)
                .containsEntry("email", "admin@tickethub.com")
                .containsEntry("role", "ADMIN");

        // 2. EventManager login -> 200 OK
        ResponseEntity<Map<String, Object>> managerResp = authController.login(
                new AuthController.LoginRequest("eventmanager", "manager123"));
        assertThat(managerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(managerResp.getBody()).containsEntry("userId", 2L)
                .containsEntry("email", "manager@tickethub.com")
                .containsEntry("role", "EVENT_MANAGER");

        // 3. Customer login -> 200 OK
        ResponseEntity<Map<String, Object>> customerResp = authController.login(
                new AuthController.LoginRequest("customer", "customer123"));
        assertThat(customerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = customerResp.getBody();
        assertThat(body).isNotNull()
                .containsEntry("userId", 101L)
                .containsEntry("email", "customer@tickethub.com")
                .containsEntry("role", "CUSTOMER");

        String token = (String) body.get("token");
        Claims claims = jwtService.parse(token);
        assertThat(jwtService.extractUserId(claims)).isEqualTo("101");
        assertThat(jwtService.extractEmail(claims)).isEqualTo("customer@tickethub.com");
        assertThat(jwtService.extractRole(claims)).isEqualTo("CUSTOMER");

        // 4. Invalid credentials -> 401 Unauthorized
        ResponseEntity<Map<String, Object>> badResp = authController.login(
                new AuthController.LoginRequest("customer", "wrongpass"));
        assertThat(badResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAcceptJwtIssuedByUserServiceWithSharedSecret() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String userServiceToken = Jwts.builder()
                .subject("jai@example.com")
                .claim("preferred_username", "jai@example.com")
                .claim("userId", 42L)
                .claim("email", "jai@example.com")
                .claim("phone", "+919876543210")
                .claim("fullName", "Jai Kishan")
                .claim("roles", List.of("ROLE_CUSTOMER"))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000L))
                .signWith(key)
                .compact();

        Claims claims = jwtService.parse(userServiceToken);
        assertThat(jwtService.extractUserId(claims)).isEqualTo("42");
        assertThat(jwtService.extractEmail(claims)).isEqualTo("jai@example.com");
        assertThat(jwtService.extractUser(claims)).isEqualTo("jai@example.com");
        assertThat(jwtService.extractRole(claims)).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldStripClientSpoofedHeadersAndPopulateVerifiedHeaders() {
        String token = jwtService.generateToken(55L, "realuser@example.com", "realuser@example.com", "CUSTOMER");

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/shows/10/seats/lock")
                .header("Authorization", "Bearer " + token)
                .header("X-Authenticated-User", "spoofed-admin")
                .header("X-Authenticated-Role", "ADMIN")
                .header("X-Authenticated-Email", "spoofed@example.com")
                .header("X-User-Id", "1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };

        gatewayFilter.filter(exchange, chain).block();

        assertThat(captured.get()).isNotNull();
        var headers = captured.get().getRequest().getHeaders();
        assertThat(headers.get("X-Authenticated-User")).containsExactly("realuser@example.com");
        assertThat(headers.get("X-Authenticated-Role")).containsExactly("CUSTOMER");
        assertThat(headers.get("X-Authenticated-Email")).containsExactly("realuser@example.com");
        assertThat(headers.get("X-User-Id")).containsExactly("55");
    }

    @Test
    void shouldAllowAllPublicEndpointsWithoutJwt() {
        assertPublicAllowed(HttpMethod.POST, "/auth/login");
        assertPublicAllowed(HttpMethod.POST, "/auth/register");
        assertPublicAllowed(HttpMethod.POST, "/auth/google");
        assertPublicAllowed(HttpMethod.GET, "/auth/google/url");
        assertPublicAllowed(HttpMethod.POST, "/api/users/register");
        assertPublicAllowed(HttpMethod.POST, "/api/users/login");
        assertPublicAllowed(HttpMethod.POST, "/api/users/auth/google");
        assertPublicAllowed(HttpMethod.GET, "/api/users/auth/google/url");
        assertPublicAllowed(HttpMethod.POST, "/api/payments/webhook");
        assertPublicAllowed(HttpMethod.GET, "/api/media/posters/test.jpg");
        assertPublicAllowed(HttpMethod.GET, "/actuator/health");
    }

    @Test
    void shouldEnforceCustomerRolePermissionsAcrossAllGatewayRoutes() {
        String customerToken = jwtService.generateToken(55L, "cust@example.com", "cust@example.com", "CUSTOMER");

        // Allowed CUSTOMER endpoints
        assertAuthenticatedAllowed(HttpMethod.GET, "/api/events/10", customerToken);
        assertAuthenticatedAllowed(HttpMethod.GET, "/api/shows/10/seats", customerToken);
        assertAuthenticatedAllowed(HttpMethod.POST, "/api/shows/10/seats/lock", customerToken);
        assertAuthenticatedAllowed(HttpMethod.POST, "/api/shows/10/seats/release", customerToken);
        assertAuthenticatedAllowed(HttpMethod.POST, "/api/bookings", customerToken);
        assertAuthenticatedAllowed(HttpMethod.GET, "/api/tickets/5", customerToken);
        assertAuthenticatedAllowed(HttpMethod.POST, "/api/payments", customerToken);
        assertAuthenticatedAllowed(HttpMethod.GET, "/api/users/me", customerToken);

        // Forbidden CUSTOMER endpoints -> 403 Forbidden
        assertAuthenticatedForbidden(HttpMethod.POST, "/api/events", customerToken);
        assertAuthenticatedForbidden(HttpMethod.GET, "/api/notifications/user/55", customerToken);
    }

    @Test
    void shouldHandleCorsPreflightOptionsRequestsForBrowser() {
        org.springframework.web.cors.reactive.CorsWebFilter corsFilter = new com.tickethub.gateway.config.CorsConfig().corsWebFilter();

        // 1. Preflight OPTIONS for /auth/login
        MockServerWebExchange ex1 = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "http://localhost:8080/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type")
                        .build());

        corsFilter.filter(ex1, exchange -> Mono.empty()).block();
        assertThat(ex1.getResponse().getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(ex1.getResponse().getHeaders().getAccessControlAllowCredentials()).isTrue();

        // 2. Preflight OPTIONS for routed API /api/events
        MockServerWebExchange ex2 = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "http://localhost:8080/api/events")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization")
                        .build());
        corsFilter.filter(ex2, exchange -> Mono.empty()).block();
        assertThat(ex2.getResponse().getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
        assertThat(ex2.getResponse().getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    private void assertPublicAllowed(HttpMethod method, String path) {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        GatewayFilterChain chain = ex -> {
            called.set(true);
            return Mono.empty();
        };
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path).build());
        gatewayFilter.filter(ex, chain).block();
        assertThat(called.get()).as("Expected public endpoint %s %s to be allowed", method, path).isTrue();
    }

    private void assertAuthenticatedAllowed(HttpMethod method, String path, String token) {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        GatewayFilterChain chain = ex -> {
            called.set(true);
            return Mono.empty();
        };
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path)
                        .header("Authorization", "Bearer " + token)
                        .build());
        gatewayFilter.filter(ex, chain).block();
        assertThat(called.get()).as("Expected authenticated endpoint %s %s to be allowed", method, path).isTrue();
    }

    private void assertAuthenticatedForbidden(HttpMethod method, String path, String token) {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        GatewayFilterChain chain = ex -> {
            called.set(true);
            return Mono.empty();
        };
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path)
                        .header("Authorization", "Bearer " + token)
                        .build());
        gatewayFilter.filter(ex, chain).block();
        assertThat(called.get()).isFalse();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

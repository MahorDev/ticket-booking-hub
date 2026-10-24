package com.tickethub.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class JwtGatewayFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {

    private final JwtService jwtService;

    public JwtGatewayFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        if (isPublicEndpoint(method, path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = jwtService.parse(token);
            String role = jwtService.extractRole(claims);
            String user = jwtService.extractUser(claims);
            String email = jwtService.extractEmail(claims);
            String userId = jwtService.extractUserId(claims);

            if (!isAllowed(method, path, role)) {
                return forbidden(exchange);
            }

            String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
            if (correlationId == null || !correlationId.matches("[A-Za-z0-9._-]{1,128}")) {
                correlationId = UUID.randomUUID().toString();
            }
            ServerHttpRequest.Builder reqBuilder = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove("X-Authenticated-User");
                        h.remove("X-Authenticated-Role");
                        h.remove("X-Authenticated-Email");
                        h.remove("X-User-Id");
                    })
                    .header("X-Authenticated-User", user)
                    .header("X-Authenticated-Role", role)
                    .header("X-Correlation-ID", correlationId);
            if (email != null && !email.isBlank()) {
                reqBuilder.header("X-Authenticated-Email", email);
            }
            if (userId != null && !userId.isBlank()) {
                reqBuilder.header("X-User-Id", userId);
            }
            ServerHttpRequest request = reqBuilder.build();
            exchange.getResponse().getHeaders().set("X-Correlation-ID", correlationId);

            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }

    private boolean isPublicEndpoint(String method, String path) {
        return method.equals("OPTIONS")
                || path.startsWith("/auth/")
                || path.equals("/api/users/register")
                || path.equals("/api/users/login")
                || path.startsWith("/api/users/auth/google")
                || (path.equals("/api/payments/webhook") && method.equals("POST"))
                || path.equals("/actuator/health")
                || (path.startsWith("/api/media/") && method.equals("GET"));
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring(7)
                : null;
    }

    private boolean isAllowed(String method, String path, String role) {
        // Public catalogue reads for authenticated users.
        if (method.equals("GET") && (
                path.startsWith("/api/events") ||
                path.startsWith("/api/venues") ||
                path.startsWith("/api/screens") ||
                path.startsWith("/api/seats") ||
                path.startsWith("/api/shows") ||
                path.startsWith("/api/show-seats") ||
                path.startsWith("/api/media"))) {
            return true;
        }

        // User profile endpoints accessible to any authenticated user.
        if (path.equals("/api/users/me") || path.startsWith("/api/users/")) {
            return "ADMIN".equals(role) || "EVENT_MANAGER".equals(role) || "CUSTOMER".equals(role);
        }

        if ("ADMIN".equals(role)) {
            return true;
        }

        // Event Manager manages catalogue/show data, but not customer/payment data.
        if ("EVENT_MANAGER".equals(role)) {
            return path.startsWith("/api/events") ||
                   path.startsWith("/api/venues") ||
                   path.startsWith("/api/screens") ||
                   path.startsWith("/api/seats") ||
                   path.startsWith("/api/shows") ||
                   path.startsWith("/api/show-seats") ||
                   path.startsWith("/api/media");
        }

        // Customer operations.
        if ("CUSTOMER".equals(role)) {
            if (method.equals("POST") && path.matches("/api/shows/[^/]+/seats/(lock|release)")) {
                return true;
            }
            return path.startsWith("/api/bookings") ||
                   path.startsWith("/api/booking-seats") ||
                   path.startsWith("/api/tickets") ||
                   path.startsWith("/api/payments");
        }

        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

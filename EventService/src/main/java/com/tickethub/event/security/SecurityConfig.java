//package com.tickethub.event.security;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.*;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.HttpStatus;
//import org.springframework.security.authentication.*;
//import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.core.userdetails.*;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.provisioning.InMemoryUserDetailsManager;
//import org.springframework.security.web.*;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
//
//import java.io.IOException;
//import java.time.LocalDateTime;
//
///**
// * Who can call what:
// *  - ADMIN            : everything
// *  - EVENT_MANAGER    : create/update/delete shows and show-seats, change show status
// *  - CUSTOMER         : read only (and only PUBLISHED events)
// *  - not logged in    : 401, wrong role : 403 (both in the standard JSON error format)
// */
//@Configuration
//public class SecurityConfig {
//
//    @Bean
//    UserDetailsService users(@Value("${app.admin.username}") String adminUser,
//                             @Value("${app.admin.password}") String adminPass,
//                             @Value("${app.manager.username}") String managerUser,
//                             @Value("${app.manager.password}") String managerPass,
//                             @Value("${app.customer.username}") String customerUser,
//                             @Value("${app.customer.password}") String customerPass) {
//        UserDetails admin = User.withUsername(adminUser).password(passwordEncoder().encode(adminPass)).roles("ADMIN").build();
//        UserDetails manager = User.withUsername(managerUser).password(passwordEncoder().encode(managerPass)).roles("EVENT_MANAGER").build();
//        UserDetails customer = User.withUsername(customerUser).password(passwordEncoder().encode(customerPass)).roles("CUSTOMER").build();
//        return new InMemoryUserDetailsManager(admin, manager, customer);
//    }
//
//    @Bean
//    PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//
//    @Bean
//    AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
//        return c.getAuthenticationManager();
//    }
//
//    @Bean
//    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt) throws Exception {
//        return http
//                .csrf(csrf -> csrf.disable())
//                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//                .exceptionHandling(e -> e
//                        .authenticationEntryPoint((req, res, ex) -> writeError(req, res, HttpStatus.UNAUTHORIZED, "Authentication required: send a valid Bearer token"))
//                        .accessDeniedHandler((req, res, ex) -> writeError(req, res, HttpStatus.FORBIDDEN, "You do not have permission to do this")))
//                .authorizeHttpRequests(a -> a
//                        // public
//                        .requestMatchers("/api/auth/login", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
//                        // shows and show-seats: ADMIN or EVENT_MANAGER
//                        .requestMatchers(HttpMethod.POST, "/api/shows/**", "/api/show-seats/**").hasAnyRole("ADMIN", "EVENT_MANAGER")
//                        .requestMatchers(HttpMethod.PUT, "/api/shows/**", "/api/show-seats/**").hasAnyRole("ADMIN", "EVENT_MANAGER")
//                        .requestMatchers(HttpMethod.PATCH, "/api/shows/**").hasAnyRole("ADMIN", "EVENT_MANAGER")
//                        .requestMatchers(HttpMethod.DELETE, "/api/shows/**", "/api/show-seats/**").hasAnyRole("ADMIN", "EVENT_MANAGER")
//                        // events, venues, screens, seats: ADMIN only
//                        .requestMatchers(HttpMethod.POST, "/api/events/**", "/api/venues/**", "/api/screens/**", "/api/seats/**").hasRole("ADMIN")
//                        .requestMatchers(HttpMethod.PUT, "/api/events/**", "/api/venues/**", "/api/screens/**", "/api/seats/**").hasRole("ADMIN")
//                        .requestMatchers(HttpMethod.PATCH, "/api/events/**").hasRole("ADMIN")
//                        .requestMatchers(HttpMethod.DELETE, "/api/events/**", "/api/venues/**", "/api/screens/**", "/api/seats/**").hasRole("ADMIN")
//                        // everything else (all GET calls): any logged-in user
//                        .anyRequest().authenticated())
//                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
//                .build();
//    }
//
//    // Security errors happen before Spring MVC, so the JSON is written by hand in the same shape as ApiError
//    private void writeError(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String message) throws IOException {
//        String path = req.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
//        res.setStatus(status.value());
//        res.setContentType("application/json");
//        res.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":" + status.value()
//                + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + message
//                + "\",\"path\":\"" + path + "\"}");
//    }
//}

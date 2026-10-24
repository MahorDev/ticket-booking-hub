//package com.tickethub.event.security;
//
//import jakarta.servlet.*;
//import jakarta.servlet.http.*;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.core.userdetails.*;
//import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class JwtAuthenticationFilter extends OncePerRequestFilter {
//
//    private final JwtService jwt;
//
//    private final UserDetailsService users;
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
//        String h = req.getHeader("Authorization");
//        if (h != null && h.startsWith("Bearer ")) {
//            String token = h.substring(7);
//            try {
//                String username = jwt.extractUsername(token);
//                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
//                    UserDetails u = users.loadUserByUsername(username);
//                    if (jwt.isValid(token, u)) {
//                        var auth = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
//                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
//                        SecurityContextHolder.getContext().setAuthentication(auth);
//                    }
//                }
//            } catch (Exception e) {
//                // bad/expired token: continue unauthenticated, the security rules will answer 401
//                log.debug("Invalid JWT: {}", e.getMessage());
//            }
//        }
//        chain.doFilter(req, res);
//    }
//}

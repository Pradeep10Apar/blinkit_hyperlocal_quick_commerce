package com.blinkit.phase1.auth.jwt;

import com.blinkit.phase1.auth.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JWT Authentication Filter.
 * 
 * Runs once per request to:
 * 1. Extract JWT from Authorization header
 * 2. Validate the token
 * 3. Set authentication in SecurityContext
 * 
 * This enables @PreAuthorize and role-based access control.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract token from header
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No token - continue without authentication (public endpoints)
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // 2. Validate token
            if (!jwtUtil.isValidAccessToken(token)) {
                log.debug("Invalid or non-access token provided");
                filterChain.doFilter(request, response);
                return;
            }

            // 3. Extract user info from token
            UUID userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);
            UserRole role = jwtUtil.extractRole(token);

            // 4. Create authentication object
            // Principal = AuthenticatedUser (custom object with user details)
            AuthenticatedUser principal = new AuthenticatedUser(userId, email, role);
            
            // Authorities for role-based access control
            List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role.name())
            );

            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
            
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 5. Set authentication in SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Authenticated user: {} with role: {}", email, role);

        } catch (Exception e) {
            log.debug("Could not authenticate token: {}", e.getMessage());
            // Don't set authentication - request will be treated as anonymous
        }

        filterChain.doFilter(request, response);
    }
}

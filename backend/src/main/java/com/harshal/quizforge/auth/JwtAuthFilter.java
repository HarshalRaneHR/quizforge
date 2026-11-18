package com.harshal.quizforge.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Error code the frontend looks for to send the user to the "Set your password" screen. */
    static final String PASSWORD_CHANGE_REQUIRED_CODE = "PASSWORD_CHANGE_REQUIRED";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String email = jwtService.extractEmail(header.substring(7));
                // Reloaded on every request: role, access and status changes apply immediately.
                UserDetails user = userDetailsService.loadUserByUsername(email);
                if (!user.isEnabled()) {
                    // Deactivated: stay unauthenticated, so the request gets 401 even with a valid token.
                    chain.doFilter(request, response);
                    return;
                }
                boolean mustChangePassword = user.getAuthorities().stream()
                        .anyMatch(a -> User.PASSWORD_CHANGE_REQUIRED.equals(a.getAuthority()));
                if (mustChangePassword && !allowedWhilePasswordChangePending(request)) {
                    writePasswordChangeRequired(response);
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | UsernameNotFoundException e) {
                // Invalid/expired token or deleted user: stay unauthenticated → 401 downstream.
            }
        }
        chain.doFilter(request, response);
    }

    /** Until they pick their own password, a user may only see who they are, change it, or log out. */
    private static boolean allowedWhilePasswordChangePending(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        return ("GET".equals(method) && "/api/me".equals(path))
                || ("POST".equals(method) && "/api/account/password".equals(path))
                || path.startsWith("/api/auth/");
    }

    private static void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"Forbidden","status":403,\
                "detail":"Please set your own password before continuing.","code":"%s"}"""
                .formatted(PASSWORD_CHANGE_REQUIRED_CODE));
    }
}

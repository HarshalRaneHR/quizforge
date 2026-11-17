package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.AuthResponse;
import com.harshal.quizforge.auth.AuthDtos.LoginRequest;
import com.harshal.quizforge.auth.AuthDtos.MeResponse;
import com.harshal.quizforge.auth.AuthDtos.RegisterRequest;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        users.save(user);
        return tokenFor(user);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        // Throws BadCredentialsException (→ 401 via GlobalExceptionHandler) on wrong email/password.
        authManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        return tokenFor(users.findByEmail(email).orElseThrow());
    }

    public MeResponse me(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
        return new MeResponse(user.getEmail(), user.getRole().name(), AuthDtos.permissionsOf(user.getRole()));
    }

    private AuthResponse tokenFor(User user) {
        String role = user.getRole().name();
        return new AuthResponse(jwt.generate(user.getEmail(), role), user.getEmail(), role,
                AuthDtos.permissionsOf(user.getRole()));
    }
}

package com.harshal.quizforge.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the ADMIN account on startup from ADMIN_EMAIL / ADMIN_PASSWORD. */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    /** The .env password is only a bootstrap: by default the admin must replace it at first login. */
    @Value("${app.admin.require-password-change:true}")
    private boolean requirePasswordChange;

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.isBlank() || users.existsByEmail(email.toLowerCase())) return;
        User admin = new User();
        admin.setEmail(email.toLowerCase());
        admin.setFullName("Administrator");
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setMustChangePassword(requirePasswordChange);
        users.save(admin);
    }
}

package com.harshal.quizforge.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    /**
     * Loaded on every authenticated request (see JwtAuthFilter), so a role change by an admin
     * takes effect immediately — the role inside an old JWT is never trusted.
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(user.authorities())   // ROLE_X + effective permissions (role defaults ± overrides)
                .disabled(!user.isActive())        // deactivated: refused at login and on every request
                .build();
    }
}

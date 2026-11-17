package com.harshal.quizforge.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/** The signed-in user as services see it: who they are and what they're allowed to do. */
public record CurrentUser(String email, Set<String> authorities) {

    public static CurrentUser of(Authentication auth) {
        return new CurrentUser(auth.getName(),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toUnmodifiableSet()));
    }

    public boolean can(Permission permission) {
        return authorities.contains(permission.authority());
    }
}

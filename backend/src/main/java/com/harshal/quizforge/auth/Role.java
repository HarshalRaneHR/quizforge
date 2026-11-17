package com.harshal.quizforge.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.harshal.quizforge.auth.Permission.*;

public enum Role {
    /** Takes quizzes and makes personal practice quizzes. Default for new sign-ups. */
    LEARNER(EnumSet.of(QUIZ_GENERATE)),
    /** Authors and publishes quizzes, runs classrooms. */
    INSTRUCTOR(EnumSet.of(QUIZ_GENERATE, QUIZ_AUTHOR, CLASSROOM_MANAGE)),
    /** Everything, including user management. */
    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return EnumSet.copyOf(permissions);
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /** ROLE_X (for hasRole checks) plus one authority per permission (for hasAuthority checks). */
    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + name()));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.authority())));
        return authorities;
    }
}

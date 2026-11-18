package com.harshal.quizforge.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "users")          // "user" is a reserved word in PostgreSQL
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.LEARNER;

    private Instant createdAt = Instant.now();

    @Column(length = 80)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Set when an admin creates or resets the account: the person must pick their own password first. */
    private boolean mustChangePassword;

    private Instant lastLoginAt;

    /**
     * Marker authority while a password change is pending. JwtAuthFilter then allows only the
     * endpoints needed to change it, whatever the role.
     */
    public static final String PASSWORD_CHANGE_REQUIRED = "account:password-change-required";

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Full name if set, otherwise the email. */
    public String displayName() {
        return fullName == null || fullName.isBlank() ? email : fullName;
    }

    /**
     * Per-user changes to the role's defaults: true = granted, false = removed.
     * Eager: it's tiny and needed on every authenticated request.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permission_override", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "permission", length = 40)
    @Column(name = "granted", nullable = false)
    private Map<Permission, Boolean> permissionOverrides = new HashMap<>();

    /** What this user may actually do: role defaults + granted − removed. Admins always have everything. */
    public Set<Permission> effectivePermissions() {
        if (role == Role.ADMIN) return EnumSet.allOf(Permission.class);
        Set<Permission> result = role.permissions();
        permissionOverrides.forEach((p, granted) -> {
            if (Boolean.TRUE.equals(granted)) result.add(p);
            else result.remove(p);
        });
        return result;
    }

    /** ROLE_X (for hasRole checks) plus one authority per effective permission. */
    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        effectivePermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.authority())));
        if (mustChangePassword) authorities.add(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED));
        return authorities;
    }

    /** Stores only the differences from the role's defaults, so role changes stay predictable. */
    public void setAccess(Set<Permission> wanted) {
        permissionOverrides.clear();
        Set<Permission> defaults = role.permissions();
        for (Permission p : Permission.adjustableOnes()) {
            boolean want = wanted.contains(p);
            if (want != defaults.contains(p)) permissionOverrides.put(p, want);
        }
    }
}

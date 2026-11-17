package com.harshal.quizforge.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor
public class RefreshToken {

    public enum RevokeReason { ROTATED, LOGOUT, REUSE_DETECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    /** SHA-256 of the raw token. The raw value only ever exists in the user's cookie. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Every token created by rotating from one login shares this id, so a whole session can be revoked. */
    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RevokeReason revokeReason;

    @Column(nullable = false)
    private Instant createdAt;
}

package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.RefreshToken.RevokeReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Row lock (SELECT ... FOR UPDATE): if two requests rotate the same token at the same moment,
     * the second waits and then sees it already revoked — a token can never be rotated twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason
            where t.familyId = :family and t.revokedAt is null""")
    int revokeFamily(@Param("family") UUID family, @Param("now") Instant now, @Param("reason") RevokeReason reason);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}

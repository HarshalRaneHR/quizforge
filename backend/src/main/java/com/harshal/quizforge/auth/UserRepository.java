package com.harshal.quizforge.auth;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRoleAndStatus(Role role, AccountStatus status);

    /** Composable filters for the admin user list (dynamic WHERE clause, no string-built JPQL). */
    final class Specs {
        private Specs() {}

        /** Matches part of the email or the full name, case-insensitive. */
        static Specification<User> textMatches(String text) {
            if (text == null || text.isBlank()) return (root, query, cb) -> null;
            String pattern = "%" + text.trim().toLowerCase() + "%";
            return (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.<String>get("fullName"), "")), pattern));
        }

        static Specification<User> hasRole(Role role) {
            return (root, query, cb) -> role == null ? null : cb.equal(root.get("role"), role);
        }

        static Specification<User> inState(AccountState state) {
            return (root, query, cb) -> switch (state) {
                case null -> null;
                case DEACTIVATED -> cb.equal(root.get("status"), AccountStatus.DEACTIVATED);
                case PENDING -> cb.and(cb.equal(root.get("status"), AccountStatus.ACTIVE), cb.isTrue(root.get("mustChangePassword")));
                case ACTIVE -> cb.and(cb.equal(root.get("status"), AccountStatus.ACTIVE), cb.isFalse(root.get("mustChangePassword")));
            };
        }
    }
}

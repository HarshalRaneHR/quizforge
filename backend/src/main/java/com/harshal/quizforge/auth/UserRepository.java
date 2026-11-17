package com.harshal.quizforge.auth;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Composable filters for the admin user list (dynamic WHERE clause, no string-built JPQL). */
    final class Specs {
        private Specs() {}

        static Specification<User> emailContains(String text) {
            return (root, query, cb) -> text == null || text.isBlank()
                    ? null
                    : cb.like(cb.lower(root.get("email")), "%" + text.trim().toLowerCase() + "%");
        }

        static Specification<User> hasRole(Role role) {
            return (root, query, cb) -> role == null ? null : cb.equal(root.get("role"), role);
        }
    }
}

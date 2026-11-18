package com.harshal.quizforge.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long>, JpaSpecificationExecutor<Quiz> {

    /** Owner fetched in the same query: list pages show the author without N+1 lookups. */
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Quiz> findAll(Specification<Quiz> spec, Pageable pageable);

    boolean existsByBuiltInSlug(String slug);

    // One query for quiz + owner + questions.
    @EntityGraph(attributePaths = {"owner", "questions"})
    Optional<Quiz> findWithQuestionsById(Long id);

    /** Composable filters (dynamic WHERE clause). A null argument means "no filter". */
    final class Specs {
        private Specs() {}

        static Specification<Quiz> ownedBy(String email) {
            return (root, q, cb) -> cb.equal(root.get("owner").get("email"), email);
        }

        static Specification<Quiz> hasVisibility(Visibility v) {
            return (root, q, cb) -> v == null ? null : cb.equal(root.get("visibility"), v);
        }

        static Specification<Quiz> hasStatus(QuizStatus s) {
            return (root, q, cb) -> s == null ? null : cb.equal(root.get("status"), s);
        }

        static Specification<Quiz> listed() {
            return hasStatus(QuizStatus.PUBLISHED).and(hasVisibility(Visibility.PUBLIC));
        }

        static Specification<Quiz> titleContains(String text) {
            return (root, q, cb) -> text == null || text.isBlank()
                    ? null
                    : cb.like(cb.lower(root.get("title")), "%" + text.trim().toLowerCase() + "%");
        }
    }
}

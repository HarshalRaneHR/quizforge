package com.harshal.quizforge.quiz;

import com.harshal.quizforge.quiz.Attempt.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    @EntityGraph(attributePaths = "quiz")
    Page<Attempt> findByUserEmail(String email, Pageable pageable);

    /** Attempt + its saved answers + quiz + quiz owner in one query. */
    @EntityGraph(attributePaths = {"answers", "quiz", "quiz.owner", "user"})
    Optional<Attempt> findWithAnswersById(Long id);

    Optional<Attempt> findFirstByQuizIdAndUserEmailAndStatus(Long quizId, String email, Status status);

    long countByQuizIdAndUserEmailAndStatus(Long quizId, String email, Status status);

    /** Has anyone other than the owner attempted this quiz? (Then it must be archived, not deleted.) */
    boolean existsByQuizIdAndUserEmailNot(Long quizId, String ownerEmail);

    void deleteByQuizId(Long quizId);
}

package com.harshal.quizforge.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Page<Quiz> findByOwnerEmail(String email, Pageable pageable);

    // One query for quiz + owner + questions (options then batch-load).
    @EntityGraph(attributePaths = {"owner", "questions"})
    Optional<Quiz> findWithQuestionsById(Long id);
}

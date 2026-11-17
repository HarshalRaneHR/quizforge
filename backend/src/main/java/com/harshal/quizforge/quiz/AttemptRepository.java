package com.harshal.quizforge.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    @EntityGraph(attributePaths = "quiz")
    Page<Attempt> findByUserEmail(String email, Pageable pageable);

    void deleteByQuizId(Long quizId);
}

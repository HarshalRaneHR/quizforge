package com.harshal.quizforge.quiz;

import com.harshal.quizforge.quiz.Attempt.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    @EntityGraph(attributePaths = "quiz")
    Page<Attempt> findByUserEmail(String email, Pageable pageable);

    /** Attempt + its saved answers + quiz + quiz owner in one query. */
    @EntityGraph(attributePaths = {"answers", "quiz", "quiz.owner", "user"})
    Optional<Attempt> findWithAnswersById(Long id);

    Optional<Attempt> findFirstByQuizIdAndUserEmailAndStatus(Long quizId, String email, Status status);

    long countByQuizIdAndUserEmailAndStatus(Long quizId, String email, Status status);

    /** Per learner and quiz: best submitted score fraction, submitted count, open count. */
    interface AttemptStats {
        Long getQuizId();
        Long getUserId();
        Double getBest();      // null when nothing submitted yet
        Long getSubmitted();
        Long getOpen();
    }

    /** One grouped query for a whole gradebook (instead of one query per learner × quiz cell). */
    @Query("""
            select a.quiz.id as quizId, a.user.id as userId,
                   max(case when a.status = 'SUBMITTED' and a.maxScore > 0 then a.score / a.maxScore end) as best,
                   sum(case when a.status = 'SUBMITTED' then 1 else 0 end) as submitted,
                   sum(case when a.status = 'IN_PROGRESS' then 1 else 0 end) as open
            from Attempt a
            where a.quiz.id in :quizIds and a.user.id in :userIds
            group by a.quiz.id, a.user.id""")
    List<AttemptStats> statsFor(@Param("quizIds") Collection<Long> quizIds, @Param("userIds") Collection<Long> userIds);

    /** Has anyone other than the owner attempted this quiz? (Then it must be archived, not deleted.) */
    boolean existsByQuizIdAndUserEmailNot(Long quizId, String ownerEmail);

    void deleteByQuizId(Long quizId);
}

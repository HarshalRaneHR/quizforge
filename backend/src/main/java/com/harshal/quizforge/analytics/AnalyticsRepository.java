package com.harshal.quizforge.analytics;

import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.quiz.Attempt;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Read-only aggregate queries for dashboards. Grouping and averaging happen in PostgreSQL;
 * the service only shapes the (small) results.
 */
public interface AnalyticsRepository extends Repository<Attempt, Long> {

    interface AttemptPoint {
        Long getAttemptId();
        Long getQuizId();
        String getQuizTitle();
        Long getUserId();
        double getScore();
        double getMaxScore();
        Instant getSubmittedAt();
        Boolean getPassed();
    }

    interface TypeAccuracy {
        QuestionType getType();
        long getAnswered();
        double getAccuracy();
    }

    interface QuestionAccuracy {
        Long getQuestionId();
        long getAnswered();
        double getAccuracy();
        long getFullyCorrect();
    }

    interface WrongResponse {
        Long getQuestionId();
        String getResponseJson();
    }

    interface RoleCount {
        com.harshal.quizforge.auth.Role getRole();
        long getCount();
    }

    // ---------------------------------------------------------------- learner

    @Query("""
            select a.id as attemptId, a.quiz.id as quizId, a.quiz.title as quizTitle, a.user.id as userId,
                   a.score as score, a.maxScore as maxScore, a.submittedAt as submittedAt, a.passed as passed
            from Attempt a
            where a.user.email = :email and a.status = 'SUBMITTED'
            order by a.submittedAt asc""")
    List<AttemptPoint> submittedByUser(@Param("email") String email);

    /** Accuracy (average share of points earned) per question type, over all my submitted answers. */
    @Query("""
            select q.type as type, count(aa) as answered,
                   avg(case when aa.maxPoints > 0 then aa.pointsAwarded / aa.maxPoints else 0 end) as accuracy
            from AttemptAnswer aa join aa.question q join aa.attempt a
            where a.user.email = :email and a.status = 'SUBMITTED'
            group by q.type""")
    List<TypeAccuracy> accuracyByType(@Param("email") String email);

    // ---------------------------------------------------------------- instructor (one quiz, learners only)

    @Query("""
            select a.id as attemptId, a.quiz.id as quizId, a.quiz.title as quizTitle, a.user.id as userId,
                   a.score as score, a.maxScore as maxScore, a.submittedAt as submittedAt, a.passed as passed
            from Attempt a
            where a.quiz.id = :quizId and a.status = 'SUBMITTED' and a.user.email <> :ownerEmail""")
    List<AttemptPoint> submittedForQuiz(@Param("quizId") Long quizId, @Param("ownerEmail") String ownerEmail);

    @Query("""
            select q.id as questionId, count(aa) as answered,
                   avg(case when aa.maxPoints > 0 then aa.pointsAwarded / aa.maxPoints else 0 end) as accuracy,
                   sum(case when aa.correct = true then 1 else 0 end) as fullyCorrect
            from AttemptAnswer aa join aa.question q join aa.attempt a
            where q.quiz.id = :quizId and a.status = 'SUBMITTED' and a.user.email <> :ownerEmail
            group by q.id""")
    List<QuestionAccuracy> accuracyByQuestion(@Param("quizId") Long quizId, @Param("ownerEmail") String ownerEmail);

    /** Wrong answers to choice-style questions, to find the most common mistake (a "distractor" analysis). */
    @Query("""
            select aa.question.id as questionId, aa.responseJson as responseJson
            from AttemptAnswer aa join aa.attempt a
            where aa.question.quiz.id = :quizId and a.status = 'SUBMITTED' and a.user.email <> :ownerEmail
              and aa.correct = false and aa.responseJson is not null and aa.question.type in :types""")
    List<WrongResponse> wrongResponses(@Param("quizId") Long quizId, @Param("ownerEmail") String ownerEmail,
                                       @Param("types") Collection<QuestionType> types);

    // ---------------------------------------------------------------- platform (admin)

    @Query("select u.role as role, count(u) as count from User u group by u.role")
    List<RoleCount> usersByRole();

    @Query("select count(q) from Quiz q where q.status = 'PUBLISHED' and q.visibility = 'PUBLIC'")
    long publishedQuizzes();

    @Query("select count(q) from Quiz q where q.status = 'DRAFT'")
    long draftQuizzes();

    @Query("select count(q) from Quiz q where q.visibility = 'PRIVATE'")
    long practiceQuizzes();

    @Query("select count(a) from Attempt a where a.status = 'SUBMITTED'")
    long submittedAttempts();

    @Query("select a.submittedAt from Attempt a where a.status = 'SUBMITTED' and a.submittedAt >= :since")
    List<Instant> submittedSince(@Param("since") Instant since);

    @Query("select count(c) from Classroom c")
    long classrooms();

    @Query("select count(j) from GenerationJob j where j.status = 'SUCCEEDED'")
    long generationSucceeded();

    @Query("select count(j) from GenerationJob j where j.status = 'FAILED'")
    long generationFailed();
}

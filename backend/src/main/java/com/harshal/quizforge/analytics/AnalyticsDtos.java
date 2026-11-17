package com.harshal.quizforge.analytics;

import com.harshal.quizforge.question.QuestionType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    // ---- learner
    public record TrendPoint(Long attemptId, String quizTitle, Instant submittedAt, int percentage) {}

    public record TypeStat(QuestionType type, long answered, int accuracyPercentage) {}

    /** Averages are null until there's at least one submitted attempt. */
    public record MyAnalytics(int attempts, int quizzesTaken, Integer averagePercentage, Integer bestPercentage,
                              int streakDays, List<TrendPoint> trend, List<TypeStat> byType, QuestionType weakestType) {}

    // ---- instructor
    public record Bucket(String label, int from, int to, long count) {}

    public record QuestionStat(Long questionId, int position, QuestionType type, String prompt, long answered,
                               Integer averagePercentage, Integer fullyCorrectPercentage,
                               String commonWrongAnswer, Long commonWrongCount) {}

    /** Learner attempts only (the author's own previews are excluded). */
    public record QuizAnalytics(Long quizId, String title, int attempts, int learners, Integer averagePercentage,
                                Integer passPercentage, Integer passRatePercentage, List<Bucket> distribution,
                                List<QuestionStat> questions) {}

    // ---- admin
    public record DayCount(LocalDate day, long count) {}

    public record PlatformStats(Map<String, Long> usersByRole, long totalUsers, long publishedQuizzes, long draftQuizzes,
                                long practiceQuizzes, long submittedAttempts, long classrooms,
                                long generationSucceeded, long generationFailed, List<DayCount> attemptsLast14Days) {}
}

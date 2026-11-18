package com.harshal.quizforge.quiz;

import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.quiz.QuizSettings.ResultsVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class QuizDtos {
    private QuizDtos() {}

    // ---------------------------------------------------------------- generation

    /** How many questions of each type to generate, and how hard. */
    public record Blueprint(
            @Schema(example = "{\"SINGLE_CHOICE\": 3, \"TRUE_FALSE\": 2, \"ORDERING\": 1}")
            @NotEmpty Map<QuestionType, @NotNull @Min(0) @Max(15) Integer> counts,
            Difficulty difficulty) {}

    public record GenerateQuizRequest(
            @Size(max = 120) String title,
            @NotBlank @Size(min = 200, max = 20000, message = "must be 200-20000 characters") String sourceText,
            @NotNull @Valid Blueprint blueprint) {}

    // ---------------------------------------------------------------- quizzes

    /** How a quiz is taken. Null limits = unlimited; omitted switches = off; omitted visibility = FULL. */
    public record Settings(
            @Min(1) @Max(100) Integer maxAttempts,
            @Min(1) @Max(100) Integer passPercentage,
            Boolean shuffleQuestions,
            Boolean shuffleOptions,
            ResultsVisibility resultsVisibility) {

        public Settings {
            shuffleQuestions = Boolean.TRUE.equals(shuffleQuestions);
            shuffleOptions = Boolean.TRUE.equals(shuffleOptions);
            if (resultsVisibility == null) resultsVisibility = ResultsVisibility.FULL;
        }

        static Settings of(QuizSettings s) {
            return new Settings(s.getMaxAttempts(), s.getPassPercentage(),
                    s.isShuffleQuestions(), s.isShuffleOptions(), s.getResultsVisibility());
        }

        void applyTo(QuizSettings s) {
            s.setMaxAttempts(maxAttempts);
            s.setPassPercentage(passPercentage);
            s.setShuffleQuestions(shuffleQuestions);
            s.setShuffleOptions(shuffleOptions);
            s.setResultsVisibility(resultsVisibility);
        }
    }

    public record QuizSummary(Long id, String title, QuizStatus status, Visibility visibility, int questionCount,
                              int totalPoints, Instant createdAt, Instant updatedAt) {}

    /** A published quiz in the Explore catalog. */
    /** {@code builtIn}: part of the QuizForge library (read-only, can't be deleted). */
    public record CatalogQuiz(Long id, String title, String description, String author, int questionCount,
                              int totalPoints, Instant publishedAt, boolean builtIn) {}

    /**
     * Quiz landing page: everything except the questions — those are only sent once an attempt starts
     * (and counts towards the attempt limit).
     */
    public record QuizDetail(Long id, String title, String description, QuizStatus status, String author,
                             int questionCount, int totalPoints, Settings settings,
                             int attemptsUsed, Integer attemptsLeft, Long openAttemptId, boolean canAuthor, boolean builtIn) {}

    // ---------------------------------------------------------------- attempts

    /**
     * A question as shown in an attempt. {@code optionOrder} (choice questions with option shuffling)
     * is the display order of option indexes; responses always use the original indexes.
     */
    public record SessionQuestion(Long id, QuestionType type, Difficulty difficulty, int points, String prompt,
                                  Map<String, Object> content, List<Integer> optionOrder) {}

    public record SavedAnswer(Long questionId, Map<String, Object> response, boolean flagged) {}

    /** An open attempt: questions in this attempt's order and the answers saved so far (for resuming). */
    public record AttemptSession(Long attemptId, Long quizId, String title, int totalPoints, Instant startedAt,
                                 List<SessionQuestion> questions, List<SavedAnswer> answers) {}

    /**
     * {@code response} shape depends on the question type; null/empty = unanswered.
     * {@code flagged} is optional (boxed: omitting it means false, it isn't a parse error).
     */
    public record SaveAnswerRequest(Map<String, Object> response, Boolean flagged) {
        public boolean isFlagged() {
            return Boolean.TRUE.equals(flagged);
        }
    }

    public record SaveAnswerResult(Instant savedAt) {}

    public record QuestionResult(SessionQuestion question, double pointsAwarded, double maxPoints, boolean correct,
                                 Map<String, Object> response, Map<String, Object> correctAnswer, String explanation) {}

    /** {@code detailsHidden}: the quiz shows learners their score only (results list is empty). */
    public record AttemptResult(Long attemptId, Long quizId, String quizTitle, Attempt.Status status, double score,
                                double maxScore, int percentage, Boolean passed, Integer passPercentage,
                                boolean detailsHidden, Instant startedAt, Instant submittedAt, List<QuestionResult> results) {}

    public record AttemptSummary(Long id, Long quizId, String quizTitle, Attempt.Status status, double score,
                                 double maxScore, int percentage, Boolean passed, Instant createdAt) {}

    static int percentage(double score, double maxScore) {
        return maxScore <= 0 ? 0 : (int) Math.round(score * 100.0 / maxScore);
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

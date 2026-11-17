package com.harshal.quizforge.quiz;

import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class QuizDtos {
    private QuizDtos() {}

    /** How many questions of each type to generate, and how hard. */
    public record Blueprint(
            @Schema(example = "{\"SINGLE_CHOICE\": 3, \"TRUE_FALSE\": 2, \"ORDERING\": 1}")
            @NotEmpty Map<QuestionType, @NotNull @Min(0) @Max(15) Integer> counts,
            Difficulty difficulty) {}

    public record GenerateQuizRequest(
            @Size(max = 120) String title,
            @NotBlank @Size(min = 200, max = 20000, message = "must be 200-20000 characters") String sourceText,
            @NotNull @Valid Blueprint blueprint) {}

    public record QuizSummary(Long id, String title, QuizStatus status, Visibility visibility, int questionCount,
                              int totalPoints, Instant createdAt, Instant updatedAt) {}

    /** A published quiz in the Explore catalog. */
    public record CatalogQuiz(Long id, String title, String description, String author, int questionCount,
                              int totalPoints, Instant publishedAt) {}

    /**
     * Sent to the browser while taking a quiz — deliberately has NO answer key.
     * {@code content} depends on {@code type} (options, items, left/right lists…).
     */
    public record QuestionView(Long id, QuestionType type, Difficulty difficulty, int points, String prompt,
                               Map<String, Object> content) {}

    public record QuizDetail(Long id, String title, String description, QuizStatus status, String author,
                             int totalPoints, List<QuestionView> questions) {}

    /** {@code response} shape depends on the question type; missing = unanswered. */
    public record AnswerSubmission(@NotNull Long questionId, Map<String, Object> response) {}

    public record SubmitAttemptRequest(@NotNull @Valid List<@NotNull AnswerSubmission> answers) {}

    public record QuestionResult(Long questionId, QuestionType type, double pointsAwarded, double maxPoints,
                                 boolean correct, Map<String, Object> response, Map<String, Object> correctAnswer,
                                 String explanation) {}

    public record AttemptResult(Long attemptId, double score, double maxScore, int percentage, List<QuestionResult> results) {}

    public record AttemptSummary(Long id, Long quizId, String quizTitle, double score, double maxScore, int percentage,
                                 Instant createdAt) {}

    static int percentage(double score, double maxScore) {
        return maxScore <= 0 ? 0 : (int) Math.round(score * 100.0 / maxScore);
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

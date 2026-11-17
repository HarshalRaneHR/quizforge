package com.harshal.quizforge.quiz;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class QuizDtos {
    private QuizDtos() {}

    public record GenerateQuizRequest(
            @Size(max = 120) String title,
            @NotBlank @Size(min = 200, max = 20000, message = "must be 200-20000 characters") String sourceText,
            @Min(3) @Max(15) int questionCount) {}

    public record QuizSummary(Long id, String title, int questionCount, Instant createdAt) {}

    // Sent to the browser while taking a quiz — deliberately has NO correct answers.
    public record QuestionView(Long id, String prompt, List<String> options) {}
    public record QuizDetail(Long id, String title, List<QuestionView> questions) {}

    public record SubmitAttemptRequest(@NotNull List<@NotNull Integer> answers) {}

    public record QuestionResult(Long questionId, int chosen, int correct, boolean isCorrect, String explanation) {}
    public record AttemptResult(Long attemptId, int score, int total, List<QuestionResult> results) {}

    public record AttemptSummary(Long id, Long quizId, String quizTitle, int score, int total, Instant createdAt) {}
}

package com.harshal.quizforge.quiz;

import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.quiz.QuizDtos.Blueprint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** Studio (quiz authoring) API shapes. */
public final class StudioDtos {
    private StudioDtos() {}

    public record CreateQuizRequest(@NotBlank @Size(max = 120) String title, @Size(max = 2000) String description) {}

    public record UpdateQuizRequest(@NotBlank @Size(max = 120) String title, @Size(max = 2000) String description) {}

    public record MatchPair(String left, String right) {}

    /**
     * A question in "natural" form, easy for a form to edit: ordering items are given in the CORRECT
     * order and matching pairs side by side. The server shuffles what learners will see.
     * Only the fields for {@code type} are used:
     * <ul>
     *   <li>SINGLE_CHOICE / MULTIPLE_CHOICE: options + correctIndexes (exactly one for single choice)</li>
     *   <li>TRUE_FALSE: answer</li>
     *   <li>FILL_BLANK: blanks = accepted answers for each ___ in the prompt</li>
     *   <li>NUMERIC: value, tolerance, unit</li>
     *   <li>ORDERING: items (correct order)</li>
     *   <li>MATCHING: pairs</li>
     * </ul>
     */
    public record AuthoringQuestion(
            @NotNull QuestionType type,
            Difficulty difficulty,
            @Min(1) @Max(10) Integer points,
            @NotBlank @Size(max = 2000) String prompt,
            @Size(max = 2000) String explanation,
            List<String> options,
            List<Integer> correctIndexes,
            Boolean answer,
            List<List<String>> blanks,
            Double value,
            Double tolerance,
            String unit,
            List<String> items,
            List<MatchPair> pairs) {}

    public record AuthoringQuestionView(Long id, int position, AuthoringQuestion question) {}

    public record StudioQuiz(Long id, String title, String description, QuizStatus status, Visibility visibility,
                             int questionCount, int totalPoints, Instant createdAt, Instant updatedAt, Instant publishedAt,
                             boolean hasLearnerAttempts, QuizDtos.Settings settings, List<AuthoringQuestionView> questions) {}

    public record ReorderRequest(@NotEmpty List<@NotNull Long> questionIds) {}

    public record StudioGenerateRequest(
            @NotBlank @Size(min = 200, max = 20000, message = "must be 200-20000 characters") String sourceText,
            @NotNull @Valid Blueprint blueprint) {}
}

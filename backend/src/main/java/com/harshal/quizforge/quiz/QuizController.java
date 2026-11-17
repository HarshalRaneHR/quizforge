package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.Permission;
import com.harshal.quizforge.common.PageResponse;
import com.harshal.quizforge.quiz.QuizDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Quizzes", description = "Generate, list, take and delete quizzes")
public class QuizController {

    private final QuizService quizzes;
    private final AttemptService attempts;

    @Operation(summary = "Generate a quiz from study material with AI",
            description = """
                    blueprint.counts sets how many questions of each type to create (1-20 in total), \
                    blueprint.difficulty sets the level. Can take up to a few minutes. Requires quiz:generate.""")
    @ApiResponse(responseCode = "201", description = "Quiz created")
    @ApiResponse(responseCode = "400", description = "Validation failed (e.g. text shorter than 200 characters, 0 or >20 questions)")
    @ApiResponse(responseCode = "502", description = "AI service failed or returned invalid output")
    @PostMapping("/quizzes/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('quiz:generate')")
    public QuizSummary generate(@Parameter(hidden = true) Authentication auth, @Valid @RequestBody GenerateQuizRequest req) {
        return quizzes.generate(auth.getName(), req);
    }

    @Operation(summary = "My quizzes (newest first)")
    @GetMapping("/quizzes")
    public PageResponse<QuizSummary> list(@Parameter(hidden = true) Authentication auth,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(quizzes.list(auth.getName(), pageable));
    }

    @Operation(summary = "Get a quiz to take", description = "Correct answers are never included.")
    @ApiResponse(responseCode = "200", description = "Questions and options")
    @ApiResponse(responseCode = "404", description = "Quiz doesn't exist or isn't yours")
    @GetMapping("/quizzes/{id}")
    public QuizDetail get(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return quizzes.getForTaking(auth.getName(), id);
    }

    @Operation(summary = "Delete a quiz and its attempts", description = "Own quizzes, or any quiz with quiz:manage-all.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Quiz doesn't exist or isn't yours")
    @DeleteMapping("/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        quizzes.delete(auth.getName(), id, canManageAll(auth));
    }

    @Operation(summary = "Submit answers and get graded results", tags = "Attempts",
            description = """
                    One entry per question: {questionId, response}. Response shape by type: \
                    SINGLE_CHOICE {selected}, MULTIPLE_CHOICE {selected:[...]}, TRUE_FALSE {value}, \
                    FILL_BLANK {values:[...]}, NUMERIC {value}, ORDERING {order:[...]}, MATCHING {pairs:[...]}. \
                    Missing answers score 0; partial credit applies to multi-select, fill-blank, ordering and matching.""")
    @ApiResponse(responseCode = "200", description = "Score, correct answers and explanations")
    @ApiResponse(responseCode = "400", description = "Unknown or duplicate question id")
    @ApiResponse(responseCode = "404", description = "Quiz doesn't exist or isn't yours")
    @PostMapping("/quizzes/{id}/attempts")
    public AttemptResult submit(@Parameter(hidden = true) Authentication auth, @PathVariable Long id,
                                @Valid @RequestBody SubmitAttemptRequest req) {
        return attempts.submit(auth.getName(), id, req);
    }

    @Operation(summary = "My attempt history (newest first)", tags = "Attempts")
    @GetMapping("/attempts/me")
    public PageResponse<AttemptSummary> myAttempts(@Parameter(hidden = true) Authentication auth,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(attempts.myAttempts(auth.getName(), pageable));
    }

    private static boolean canManageAll(Authentication auth) {
        String needed = Permission.QUIZ_MANAGE_ALL.authority();
        return auth.getAuthorities().stream().anyMatch(a -> needed.equals(a.getAuthority()));
    }
}

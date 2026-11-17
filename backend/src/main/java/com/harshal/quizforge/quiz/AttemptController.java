package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.common.PageResponse;
import com.harshal.quizforge.quiz.QuizDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Attempts", description = "Start or resume, autosave, submit and review quiz attempts")
public class AttemptController {

    private final AttemptService attempts;

    @Operation(summary = "Start an attempt (or resume the open one)",
            description = "Returns the questions in this attempt's order plus any answers already saved.")
    @ApiResponse(responseCode = "200", description = "Attempt session")
    @ApiResponse(responseCode = "404", description = "Quiz doesn't exist or isn't available to you")
    @ApiResponse(responseCode = "409", description = "No attempts left")
    @PostMapping("/quizzes/{quizId}/attempts")
    public AttemptSession start(@Parameter(hidden = true) Authentication auth, @PathVariable Long quizId) {
        return attempts.start(CurrentUser.of(auth), quizId);
    }

    @Operation(summary = "Autosave one answer",
            description = """
                    Response shape by type: SINGLE_CHOICE {selected}, MULTIPLE_CHOICE {selected:[...]}, \
                    TRUE_FALSE {value}, FILL_BLANK {values:[...]}, NUMERIC {value}, ORDERING {order:[...]}, \
                    MATCHING {pairs:[...]}. Choice indexes are the ORIGINAL option indexes (not the shuffled display order).""")
    @ApiResponse(responseCode = "409", description = "Attempt already submitted")
    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    public SaveAnswerResult save(@Parameter(hidden = true) Authentication auth, @PathVariable Long attemptId,
                                 @PathVariable Long questionId, @RequestBody SaveAnswerRequest req) {
        return attempts.saveAnswer(CurrentUser.of(auth), attemptId, questionId, req);
    }

    @Operation(summary = "Submit: grade the saved answers", description = "Idempotent: submitting again returns the same result.")
    @PostMapping("/attempts/{attemptId}/submit")
    public AttemptResult submit(@Parameter(hidden = true) Authentication auth, @PathVariable Long attemptId) {
        return attempts.submit(CurrentUser.of(auth), attemptId);
    }

    @Operation(summary = "Review a finished attempt",
            description = "Per-question results are omitted when the quiz shows learners their score only.")
    @ApiResponse(responseCode = "409", description = "Still in progress")
    @GetMapping("/attempts/{attemptId}")
    public AttemptResult get(@Parameter(hidden = true) Authentication auth, @PathVariable Long attemptId) {
        return attempts.get(CurrentUser.of(auth), attemptId);
    }

    @Operation(summary = "My attempt history (newest first)")
    @GetMapping("/attempts/me")
    public PageResponse<AttemptSummary> myAttempts(@Parameter(hidden = true) Authentication auth,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(attempts.myAttempts(CurrentUser.of(auth), pageable));
    }
}

package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.quiz.GenerationService.JobRequest;
import com.harshal.quizforge.quiz.GenerationService.JobView;
import com.harshal.quizforge.quiz.StudioDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Quiz authoring (instructors and admins). Every method requires quiz:author (enforced in StudioService). */
@RestController
@RequestMapping("/api/studio/quizzes")
@RequiredArgsConstructor
@Tag(name = "Studio", description = "Build, edit and publish quizzes (requires quiz:author)")
@ApiResponse(responseCode = "403", description = "Not an instructor, or not your quiz")
@ApiResponse(responseCode = "409", description = "Not allowed in the quiz's current status")
public class StudioController {

    private final StudioService studio;
    private final GenerationService generation;

    @Operation(summary = "Create an empty draft quiz")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudioQuiz create(@Parameter(hidden = true) Authentication auth, @Valid @RequestBody CreateQuizRequest req) {
        return studio.create(CurrentUser.of(auth), req);
    }

    @Operation(summary = "Get a quiz for editing", description = "Includes answer keys, in the editor's natural form.")
    @GetMapping("/{id}")
    public StudioQuiz get(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return studio.get(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Update title and description")
    @PutMapping("/{id}")
    public StudioQuiz update(@Parameter(hidden = true) Authentication auth, @PathVariable Long id, @Valid @RequestBody UpdateQuizRequest req) {
        return studio.update(CurrentUser.of(auth), id, req);
    }

    @Operation(summary = "Generate questions with AI and add them to this draft",
            description = "Background job; poll GET /api/generation-jobs/{id}.")
    @PostMapping("/{id}/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobView generate(@Parameter(hidden = true) Authentication auth, @PathVariable Long id, @Valid @RequestBody StudioGenerateRequest req) {
        return generation.startAppend(CurrentUser.of(auth), id, new JobRequest(null, req.sourceText(), req.blueprint()));
    }

    @Operation(summary = "Add a question (draft only)")
    @ApiResponse(responseCode = "400", description = "Invalid question (all problems listed)")
    @PostMapping("/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthoringQuestionView addQuestion(@Parameter(hidden = true) Authentication auth, @PathVariable Long id,
                                             @Valid @RequestBody AuthoringQuestion question) {
        return studio.addQuestion(CurrentUser.of(auth), id, question);
    }

    @Operation(summary = "Replace a question (draft only)")
    @PutMapping("/{id}/questions/{questionId}")
    public AuthoringQuestionView updateQuestion(@Parameter(hidden = true) Authentication auth, @PathVariable Long id,
                                                @PathVariable Long questionId, @Valid @RequestBody AuthoringQuestion question) {
        return studio.updateQuestion(CurrentUser.of(auth), id, questionId, question);
    }

    @Operation(summary = "Delete a question (draft only)")
    @DeleteMapping("/{id}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@Parameter(hidden = true) Authentication auth, @PathVariable Long id, @PathVariable Long questionId) {
        studio.deleteQuestion(CurrentUser.of(auth), id, questionId);
    }

    @Operation(summary = "Reorder questions (draft only)", description = "questionIds = every question id in the new order.")
    @PutMapping("/{id}/questions/order")
    public StudioQuiz reorder(@Parameter(hidden = true) Authentication auth, @PathVariable Long id, @Valid @RequestBody ReorderRequest req) {
        return studio.reorder(CurrentUser.of(auth), id, req);
    }

    @Operation(summary = "Publish: learners can find and take it", description = "DRAFT → PUBLISHED; needs at least one question.")
    @PostMapping("/{id}/publish")
    public StudioQuiz publish(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return studio.publish(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Back to draft for editing", description = "PUBLISHED → DRAFT, only if no learner has attempted it.")
    @PostMapping("/{id}/unpublish")
    public StudioQuiz unpublish(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return studio.unpublish(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Archive: hide from learners, keep results", description = "PUBLISHED → ARCHIVED.")
    @PostMapping("/{id}/archive")
    public StudioQuiz archive(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return studio.archive(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Restore an archived quiz", description = "ARCHIVED → PUBLISHED.")
    @PostMapping("/{id}/restore")
    public StudioQuiz restore(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return studio.restore(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Duplicate as a new draft", description = "The way to change a quiz learners have already taken.")
    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public StudioQuiz duplicate(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return studio.duplicate(CurrentUser.of(auth), id);
    }
}

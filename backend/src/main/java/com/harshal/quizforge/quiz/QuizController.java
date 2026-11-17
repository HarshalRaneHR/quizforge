package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.common.PageResponse;
import com.harshal.quizforge.quiz.GenerationService.JobRequest;
import com.harshal.quizforge.quiz.GenerationService.JobView;
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

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Quizzes", description = "Practice quizzes, the Explore catalog, taking and deleting quizzes")
public class QuizController {

    private final QuizService quizzes;
    private final GenerationService generation;

    @Operation(summary = "Start generating a private practice quiz with AI",
            description = """
                    Runs in the background: returns a job to poll at GET /api/generation-jobs/{id}. \
                    blueprint.counts sets how many questions of each type (1-20 total), blueprint.difficulty the level. \
                    One job at a time per user. Requires quiz:generate.""")
    @ApiResponse(responseCode = "202", description = "Job started")
    @ApiResponse(responseCode = "400", description = "Validation failed (text 200-20000 characters, 1-20 questions)")
    @ApiResponse(responseCode = "409", description = "A generation job is already running for you")
    @PostMapping("/quizzes/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('quiz:generate')")
    public JobView generate(@Parameter(hidden = true) Authentication auth, @Valid @RequestBody GenerateQuizRequest req) {
        return generation.startPracticeQuiz(CurrentUser.of(auth), new JobRequest(req.title(), req.sourceText(), req.blueprint()));
    }

    @Operation(summary = "Progress of a generation job", tags = "Generation",
            description = "Poll until status is SUCCEEDED (quizId set) or FAILED (error set).")
    @GetMapping("/generation-jobs/{id}")
    public JobView job(@Parameter(hidden = true) Authentication auth, @PathVariable UUID id) {
        return generation.get(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Quizzes I own", description = "Filter by visibility (PRIVATE practice / PUBLIC authored) and status.")
    @GetMapping("/quizzes")
    public PageResponse<QuizSummary> list(@Parameter(hidden = true) Authentication auth,
            @RequestParam(required = false) Visibility visibility,
            @RequestParam(required = false) QuizStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(quizzes.listMine(CurrentUser.of(auth), visibility, status, pageable));
    }

    @Operation(summary = "Explore published quizzes", description = "Published, public quizzes from instructors; search by title.")
    @GetMapping("/quizzes/explore")
    public PageResponse<CatalogQuiz> explore(@RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 12, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(quizzes.explore(search, pageable));
    }

    @Operation(summary = "Quiz landing page",
            description = "Title, settings and your attempts so far. Questions are only returned by starting an attempt.")
    @ApiResponse(responseCode = "404", description = "Quiz doesn't exist or isn't available to you")
    @GetMapping("/quizzes/{id}")
    public QuizDetail get(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return quizzes.getOverview(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Delete a quiz and its attempts",
            description = "Owner (or quiz:manage-all). Quizzes learners have attempted must be archived instead.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Quiz doesn't exist or isn't available to you")
    @ApiResponse(responseCode = "409", description = "Learners have attempted it; archive instead")
    @DeleteMapping("/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        quizzes.delete(CurrentUser.of(auth), id);
    }

}

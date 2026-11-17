package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.PageResponse;
import com.harshal.quizforge.quiz.QuizDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizzes;
    private final AttemptService attempts;

    @PostMapping("/quizzes/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizSummary generate(Authentication auth, @Valid @RequestBody GenerateQuizRequest req) {
        return quizzes.generate(auth.getName(), req);
    }

    @GetMapping("/quizzes")
    public PageResponse<QuizSummary> list(Authentication auth,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(quizzes.list(auth.getName(), pageable));
    }

    @GetMapping("/quizzes/{id}")
    public QuizDetail get(Authentication auth, @PathVariable Long id) {
        return quizzes.getForTaking(auth.getName(), id);
    }

    @DeleteMapping("/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        quizzes.delete(auth.getName(), id, isAdmin(auth));
    }

    @PostMapping("/quizzes/{id}/attempts")
    public AttemptResult submit(Authentication auth, @PathVariable Long id,
                                @Valid @RequestBody SubmitAttemptRequest req) {
        return attempts.submit(auth.getName(), id, req);
    }

    @GetMapping("/attempts/me")
    public PageResponse<AttemptSummary> myAttempts(Authentication auth,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(attempts.myAttempts(auth.getName(), pageable));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}

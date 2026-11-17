package com.harshal.quizforge.quiz;

import com.harshal.quizforge.quiz.QuizDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final AttemptRepository attemptRepo;
    private final QuizService quizService;

    @Transactional
    public AttemptResult submit(String email, Long quizId, SubmitAttemptRequest req) {
        Quiz quiz = quizService.getOwnedQuiz(email, quizId);
        // Grading happens on the server: the client never sees correct answers before submitting.
        List<QuestionResult> results = Scoring.grade(quiz.getQuestions(), req.answers());
        int score = (int) results.stream().filter(QuestionResult::isCorrect).count();

        Attempt attempt = new Attempt();
        attempt.setUser(quiz.getOwner());
        attempt.setQuiz(quiz);
        attempt.setScore(score);
        attempt.setTotal(results.size());
        attemptRepo.save(attempt);

        return new AttemptResult(attempt.getId(), score, results.size(), results);
    }

    @Transactional(readOnly = true)
    public Page<AttemptSummary> myAttempts(String email, Pageable pageable) {
        return attemptRepo.findByUserEmail(email, pageable)
                .map(a -> new AttemptSummary(a.getId(), a.getQuiz().getId(), a.getQuiz().getTitle(),
                        a.getScore(), a.getTotal(), a.getCreatedAt()));
    }
}

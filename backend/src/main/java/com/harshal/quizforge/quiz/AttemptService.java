package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.QuizDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.harshal.quizforge.quiz.QuizDtos.percentage;
import static com.harshal.quizforge.quiz.QuizDtos.round2;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private static final double FULL_MARKS = 1.0 - 1e-9;

    private final AttemptRepository attemptRepo;
    private final QuizService quizService;
    private final QuestionTypeRegistry registry;
    private final UserRepository users;

    /** Grades on the server: the client never sees answer keys before submitting. */
    @Transactional
    public AttemptResult submit(CurrentUser user, Long quizId, SubmitAttemptRequest req) {
        Quiz quiz = quizService.getTakeable(user, quizId);
        Map<Long, Map<String, Object>> responses = indexByQuestion(req, quiz);

        Attempt attempt = new Attempt();
        // The person taking the quiz, not the quiz owner (published quizzes are taken by many learners).
        attempt.setUser(users.findByEmail(user.email()).orElseThrow(() -> new NotFoundException("User not found")));
        attempt.setQuiz(quiz);
        List<QuestionResult> results = new ArrayList<>();
        double score = 0;
        double max = 0;

        for (Question q : quiz.getQuestions()) {
            Map<String, Object> response = responses.get(q.getId());
            double fraction = registry.grade(q.getType(), q.getContentJson(), q.getAnswerKeyJson(), response);
            double points = round2(fraction * q.getPoints());
            boolean correct = fraction >= FULL_MARKS;

            AttemptAnswer answer = new AttemptAnswer();
            answer.setQuestion(q);
            answer.setResponseJson(response == null ? null : registry.toJson(response));
            answer.setPointsAwarded(points);
            answer.setMaxPoints(q.getPoints());
            answer.setCorrect(correct);
            attempt.addAnswer(answer);

            results.add(new QuestionResult(q.getId(), q.getType(), points, q.getPoints(), correct, response,
                    registry.toMap(q.getAnswerKeyJson()), q.getExplanation()));
            score += points;
            max += q.getPoints();
        }

        attempt.setScore(round2(score));
        attempt.setMaxScore(max);
        attemptRepo.save(attempt);
        return new AttemptResult(attempt.getId(), attempt.getScore(), max, percentage(attempt.getScore(), max), results);
    }

    @Transactional(readOnly = true)
    public Page<AttemptSummary> myAttempts(CurrentUser user, Pageable pageable) {
        return attemptRepo.findByUserEmail(user.email(), pageable)
                .map(a -> new AttemptSummary(a.getId(), a.getQuiz().getId(), a.getQuiz().getTitle(),
                        a.getScore(), a.getMaxScore(), percentage(a.getScore(), a.getMaxScore()), a.getCreatedAt()));
    }

    private static Map<Long, Map<String, Object>> indexByQuestion(SubmitAttemptRequest req, Quiz quiz) {
        Set<Long> questionIds = quiz.getQuestions().stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (AnswerSubmission a : req.answers()) {
            if (!questionIds.contains(a.questionId())) {
                throw new BadRequestException("Question " + a.questionId() + " is not part of this quiz");
            }
            if (byId.put(a.questionId(), a.response()) != null) {
                throw new BadRequestException("Question " + a.questionId() + " was answered more than once");
            }
        }
        return byId;
    }
}

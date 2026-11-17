package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.Attempt.Status;
import com.harshal.quizforge.quiz.QuizDtos.*;
import com.harshal.quizforge.quiz.QuizSettings.ResultsVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import static com.harshal.quizforge.quiz.QuizDtos.percentage;
import static com.harshal.quizforge.quiz.QuizDtos.round2;

/**
 * Attempt lifecycle: start (or resume) → autosave answers → submit → review.
 * Grading always happens on the server from the saved answers; the client never sees answer keys
 * before submitting.
 */
@Service
@RequiredArgsConstructor
public class AttemptService {

    private static final double FULL_MARKS = 1.0 - 1e-9;
    private static final SecureRandom SEEDS = new SecureRandom();

    private final AttemptRepository attempts;
    private final QuizService quizService;
    private final QuestionTypeRegistry registry;
    private final QuestionMapper questions;
    private final UserRepository users;
    private final Clock clock;

    /** Opens a new attempt, or resumes the one already open (closing the tab never loses answers). */
    @Transactional
    public AttemptSession start(CurrentUser user, Long quizId) {
        Quiz quiz = quizService.getTakeable(user, quizId);
        if (quiz.getQuestions().isEmpty()) throw new BadRequestException("This quiz has no questions yet");

        var open = attempts.findFirstByQuizIdAndUserEmailAndStatus(quizId, user.email(), Status.IN_PROGRESS);
        if (open.isPresent()) return session(open.get(), quiz);

        Integer left = quizService.attemptsLeft(user, quiz);
        if (left != null && left <= 0) {
            throw new ConflictException("You have used all " + quiz.getSettings().getMaxAttempts() + " attempts for this quiz");
        }

        Attempt attempt = new Attempt();
        attempt.setUser(users.findByEmail(user.email()).orElseThrow(() -> new NotFoundException("User not found")));
        attempt.setQuiz(quiz);
        attempt.setStartedAt(clock.instant());
        attempt.setShuffleSeed(SEEDS.nextLong());
        attempt.setMaxScore(quiz.getTotalPoints());
        // A partial unique index allows only one open attempt per learner and quiz: a double-click
        // "Start" race ends in a constraint violation (409) instead of two attempts.
        attempts.saveAndFlush(attempt);
        return session(attempt, quiz);
    }

    /** Autosave one answer (and its "flag for review" mark) while the attempt is open. */
    @Transactional
    public SaveAnswerResult saveAnswer(CurrentUser user, Long attemptId, Long questionId, SaveAnswerRequest req) {
        Attempt attempt = ownOpenAttempt(user, attemptId);
        Question question = attempt.getQuiz().getQuestions().stream()
                .filter(q -> q.getId().equals(questionId)).findFirst()
                .orElseThrow(() -> new BadRequestException("Question " + questionId + " is not part of this quiz"));

        AttemptAnswer answer = attempt.answerFor(questionId).orElseGet(() -> {
            AttemptAnswer a = new AttemptAnswer();
            a.setQuestion(question);
            a.setMaxPoints(question.getPoints());
            attempt.addAnswer(a);
            return a;
        });
        boolean empty = req.response() == null || req.response().isEmpty();
        answer.setResponseJson(empty ? null : registry.toJson(req.response()));
        answer.setFlagged(req.isFlagged());
        Instant now = clock.instant();
        answer.setUpdatedAt(now);
        return new SaveAnswerResult(now);
    }

    /** Grades the saved answers and closes the attempt. Submitting twice returns the same result. */
    @Transactional
    public AttemptResult submit(CurrentUser user, Long attemptId) {
        Attempt attempt = ownAttempt(user, attemptId);
        if (attempt.isOpen()) grade(attempt);
        return result(attempt, user);
    }

    /** Review a finished attempt (the learner who took it, or the quiz's author/admin). */
    @Transactional(readOnly = true)
    public AttemptResult get(CurrentUser user, Long attemptId) {
        Attempt attempt = attempts.findWithAnswersById(attemptId).orElseThrow(() -> new NotFoundException("Attempt not found"));
        boolean mine = attempt.getUser().getEmail().equals(user.email());
        if (!mine && !QuizPolicy.canAuthor(attempt.getQuiz(), user)) throw new NotFoundException("Attempt not found");
        if (attempt.isOpen()) throw new ConflictException("This attempt is still in progress");
        return result(attempt, user);
    }

    @Transactional(readOnly = true)
    public Page<AttemptSummary> myAttempts(CurrentUser user, Pageable pageable) {
        return attempts.findByUserEmail(user.email(), pageable)
                .map(a -> new AttemptSummary(a.getId(), a.getQuiz().getId(), a.getQuiz().getTitle(), a.getStatus(),
                        a.getScore(), a.getMaxScore(), percentage(a.getScore(), a.getMaxScore()), a.getPassed(), a.getCreatedAt()));
    }

    // ---------------------------------------------------------------- grading

    void grade(Attempt attempt) {
        Quiz quiz = attempt.getQuiz();
        double score = 0;
        double max = 0;
        for (Question q : quiz.getQuestions()) {
            AttemptAnswer answer = attempt.answerFor(q.getId()).orElseGet(() -> {
                AttemptAnswer a = new AttemptAnswer();   // unanswered questions are recorded as 0 points
                a.setQuestion(q);
                attempt.addAnswer(a);
                return a;
            });
            Map<String, Object> response = answer.getResponseJson() == null ? null : registry.toMap(answer.getResponseJson());
            double fraction = registry.grade(q.getType(), q.getContentJson(), q.getAnswerKeyJson(), response);
            double points = round2(fraction * q.getPoints());
            answer.setPointsAwarded(points);
            answer.setMaxPoints(q.getPoints());
            answer.setCorrect(fraction >= FULL_MARKS);
            score += points;
            max += q.getPoints();
        }
        attempt.setScore(round2(score));
        attempt.setMaxScore(max);
        Integer pass = quiz.getSettings().getPassPercentage();
        attempt.setPassed(pass == null ? null : percentage(attempt.getScore(), max) >= pass);
        attempt.setStatus(Status.SUBMITTED);
        attempt.setSubmittedAt(clock.instant());
    }

    // ---------------------------------------------------------------- views

    /** Question order and option order are derived from the attempt's seed: a resumed attempt looks the same. */
    List<Question> orderedQuestions(Attempt attempt) {
        List<Question> list = new ArrayList<>(attempt.getQuiz().getQuestions());
        if (attempt.getQuiz().getSettings().isShuffleQuestions()) Collections.shuffle(list, new Random(attempt.getShuffleSeed()));
        return list;
    }

    List<Integer> optionOrder(Attempt attempt, Question q) {
        boolean choice = q.getType() == QuestionType.SINGLE_CHOICE || q.getType() == QuestionType.MULTIPLE_CHOICE;
        if (!choice || !attempt.getQuiz().getSettings().isShuffleOptions()) return null;
        int n = ((List<?>) registry.toMap(q.getContentJson()).get("options")).size();
        List<Integer> order = new ArrayList<>(IntStream.range(0, n).boxed().toList());
        Collections.shuffle(order, new Random(attempt.getShuffleSeed() * 31 + q.getId()));
        return order;
    }

    private AttemptSession session(Attempt attempt, Quiz quiz) {
        List<SessionQuestion> qs = orderedQuestions(attempt).stream()
                .map(q -> questions.toSessionQuestion(q, optionOrder(attempt, q))).toList();
        List<SavedAnswer> saved = attempt.getAnswers().stream()
                .map(a -> new SavedAnswer(a.getQuestion().getId(),
                        a.getResponseJson() == null ? null : registry.toMap(a.getResponseJson()), a.isFlagged()))
                .toList();
        return new AttemptSession(attempt.getId(), quiz.getId(), quiz.getTitle(), quiz.getTotalPoints(),
                attempt.getStartedAt(), qs, saved);
    }

    private AttemptResult result(Attempt attempt, CurrentUser viewer) {
        Quiz quiz = attempt.getQuiz();
        boolean hidden = quiz.getSettings().getResultsVisibility() == ResultsVisibility.SCORE_ONLY
                && !QuizPolicy.canAuthor(quiz, viewer);
        List<QuestionResult> results = hidden ? List.of() : orderedQuestions(attempt).stream().map(q -> {
            AttemptAnswer a = attempt.answerFor(q.getId()).orElse(null);
            Map<String, Object> response = a == null || a.getResponseJson() == null ? null : registry.toMap(a.getResponseJson());
            return new QuestionResult(questions.toSessionQuestion(q, optionOrder(attempt, q)),
                    a == null ? 0 : a.getPointsAwarded(), q.getPoints(), a != null && a.isCorrect(),
                    response, registry.toMap(q.getAnswerKeyJson()), q.getExplanation());
        }).toList();
        return new AttemptResult(attempt.getId(), quiz.getId(), quiz.getTitle(), attempt.getStatus(), attempt.getScore(),
                attempt.getMaxScore(), percentage(attempt.getScore(), attempt.getMaxScore()), attempt.getPassed(),
                quiz.getSettings().getPassPercentage(), hidden, attempt.getStartedAt(), attempt.getSubmittedAt(), results);
    }

    private Attempt ownAttempt(CurrentUser user, Long attemptId) {
        Attempt attempt = attempts.findWithAnswersById(attemptId).orElseThrow(() -> new NotFoundException("Attempt not found"));
        if (!attempt.getUser().getEmail().equals(user.email())) throw new NotFoundException("Attempt not found");
        return attempt;
    }

    private Attempt ownOpenAttempt(CurrentUser user, Long attemptId) {
        Attempt attempt = ownAttempt(user, attemptId);
        if (!attempt.isOpen()) throw new ConflictException("This attempt has already been submitted");
        return attempt;
    }
}

package com.harshal.quizforge.analytics;

import com.harshal.quizforge.analytics.AnalyticsDtos.*;
import com.harshal.quizforge.analytics.AnalyticsRepository.AttemptPoint;
import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.Question;
import com.harshal.quizforge.quiz.Quiz;
import com.harshal.quizforge.quiz.QuizPolicy;
import com.harshal.quizforge.quiz.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int TREND_POINTS = 20;
    private static final List<QuestionType> CHOICE_TYPES =
            List.of(QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE);

    private final AnalyticsRepository repo;
    private final QuizRepository quizzes;
    private final QuestionTypeRegistry registry;
    private final Clock clock;

    // ---------------------------------------------------------------- learner

    public MyAnalytics mine(CurrentUser user) {
        List<AttemptPoint> points = repo.submittedByUser(user.email());
        List<Integer> pcts = points.stream().map(AnalyticsService::pct).toList();
        List<TrendPoint> trend = points.stream().skip(Math.max(0, points.size() - TREND_POINTS))
                .map(p -> new TrendPoint(p.getAttemptId(), p.getQuizTitle(), p.getSubmittedAt(), pct(p))).toList();
        List<TypeStat> byType = repo.accuracyByType(user.email()).stream()
                .map(t -> new TypeStat(t.getType(), t.getAnswered(), (int) Math.round(t.getAccuracy() * 100)))
                .sorted(Comparator.comparing(TypeStat::type)).toList();
        // Weakest type: lowest accuracy among types with enough answers to mean something.
        QuestionType weakest = byType.stream().filter(t -> t.answered() >= 3)
                .min(Comparator.comparingInt(TypeStat::accuracyPercentage)).map(TypeStat::type).orElse(null);

        return new MyAnalytics(points.size(), (int) points.stream().map(AttemptPoint::getQuizId).distinct().count(),
                average(pcts), pcts.stream().max(Integer::compare).orElse(null),
                streak(points.stream().map(p -> p.getSubmittedAt().atZone(ZoneOffset.UTC).toLocalDate()).collect(Collectors.toSet())),
                trend, byType, weakest);
    }

    /** Consecutive days (UTC) with at least one submitted attempt, ending today or yesterday. */
    int streak(Set<LocalDate> days) {
        LocalDate day = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!days.contains(day)) day = day.minusDays(1);
        int streak = 0;
        while (days.contains(day)) { streak++; day = day.minusDays(1); }
        return streak;
    }

    // ---------------------------------------------------------------- instructor

    /** How learners did on one of my quizzes: score distribution and per-question difficulty. */
    public QuizAnalytics forQuiz(CurrentUser user, Long quizId) {
        Quiz quiz = quizzes.findWithQuestionsById(quizId).orElseThrow(() -> new NotFoundException("Quiz not found"));
        if (!QuizPolicy.canAuthor(quiz, user)) throw new NotFoundException("Quiz not found");
        String owner = quiz.getOwner().getEmail();

        List<AttemptPoint> points = repo.submittedForQuiz(quizId, owner);
        List<Integer> pcts = points.stream().map(AnalyticsService::pct).toList();
        Integer passMark = quiz.getSettings().getPassPercentage();
        Integer passRate = passMark == null || points.isEmpty() ? null
                : (int) Math.round(100.0 * points.stream().filter(p -> Boolean.TRUE.equals(p.getPassed())).count() / points.size());

        Map<Long, AnalyticsRepository.QuestionAccuracy> acc = repo.accuracyByQuestion(quizId, owner).stream()
                .collect(Collectors.toMap(AnalyticsRepository.QuestionAccuracy::getQuestionId, Function.identity()));
        Map<Long, Map.Entry<String, Long>> commonWrong = commonWrongAnswers(quiz, owner);

        List<QuestionStat> questions = quiz.getQuestions().stream().map(q -> {
            var a = acc.get(q.getId());
            var wrong = commonWrong.get(q.getId());
            return new QuestionStat(q.getId(), q.getPosition() + 1, q.getType(), q.getPrompt(),
                    a == null ? 0 : a.getAnswered(),
                    a == null ? null : (int) Math.round(a.getAccuracy() * 100),
                    a == null || a.getAnswered() == 0 ? null : (int) Math.round(100.0 * a.getFullyCorrect() / a.getAnswered()),
                    wrong == null ? null : wrong.getKey(), wrong == null ? null : wrong.getValue());
        }).toList();

        return new QuizAnalytics(quizId, quiz.getTitle(), points.size(),
                (int) points.stream().map(AttemptPoint::getUserId).distinct().count(),
                average(pcts), passMark, passRate, distribution(pcts), questions);
    }

    /** Ten 10-point buckets; 100% goes into the last one. */
    static List<Bucket> distribution(List<Integer> pcts) {
        long[] counts = new long[10];
        pcts.forEach(p -> counts[Math.min(9, Math.max(0, p) / 10)]++);
        return IntStream.range(0, 10).mapToObj(i -> {
            int from = i * 10, to = i == 9 ? 100 : from + 9;
            return new Bucket(from + "–" + to, from, to, counts[i]);
        }).toList();
    }

    private Map<Long, Map.Entry<String, Long>> commonWrongAnswers(Quiz quiz, String owner) {
        Map<Long, Question> byId = quiz.getQuestions().stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, Map<String, Long>> tallies = new HashMap<>();
        for (var w : repo.wrongResponses(quiz.getId(), owner, CHOICE_TYPES)) {
            Question q = byId.get(w.getQuestionId());
            String label = q == null ? null : describe(q, registry.toMap(w.getResponseJson()));
            if (label != null) tallies.computeIfAbsent(q.getId(), k -> new HashMap<>()).merge(label, 1L, Long::sum);
        }
        Map<Long, Map.Entry<String, Long>> result = new HashMap<>();
        tallies.forEach((qid, t) -> t.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> result.put(qid, e)));
        return result;
    }

    /** Human-readable wrong answer, e.g. "Rome" or "4 + 9" or "False". */
    private String describe(Question q, Map<String, Object> response) {
        try {
            return switch (q.getType()) {
                case TRUE_FALSE -> Boolean.TRUE.equals(response.get("value")) ? "True" : "False";
                case SINGLE_CHOICE -> options(q).get(((Number) response.get("selected")).intValue());
                case MULTIPLE_CHOICE -> ((List<?>) response.get("selected")).stream()
                        .map(i -> options(q).get(((Number) i).intValue())).collect(Collectors.joining(" + "));
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;   // malformed or partial response: skip it
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> options(Question q) {
        return (List<String>) registry.toMap(q.getContentJson()).get("options");
    }

    // ---------------------------------------------------------------- admin

    @PreAuthorize("hasAuthority('user:read')")
    public PlatformStats platform() {
        Map<String, Long> roles = new TreeMap<>();
        repo.usersByRole().forEach(r -> roles.put(r.getRole().name(), r.getCount()));

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate first = today.minusDays(13);
        Map<LocalDate, Long> perDay = repo.submittedSince(first.atStartOfDay(ZoneOffset.UTC).toInstant()).stream()
                .collect(Collectors.groupingBy(i -> i.atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
        List<DayCount> days = IntStream.range(0, 14).mapToObj(first::plusDays)
                .map(d -> new DayCount(d, perDay.getOrDefault(d, 0L))).toList();

        return new PlatformStats(roles, roles.values().stream().mapToLong(Long::longValue).sum(),
                repo.publishedQuizzes(), repo.draftQuizzes(), repo.practiceQuizzes(), repo.submittedAttempts(),
                repo.classrooms(), repo.generationSucceeded(), repo.generationFailed(), days);
    }

    // ---------------------------------------------------------------- helpers

    private static int pct(AttemptPoint p) {
        return p.getMaxScore() <= 0 ? 0 : (int) Math.round(p.getScore() * 100.0 / p.getMaxScore());
    }

    private static Integer average(List<Integer> values) {
        return values.isEmpty() ? null : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
}

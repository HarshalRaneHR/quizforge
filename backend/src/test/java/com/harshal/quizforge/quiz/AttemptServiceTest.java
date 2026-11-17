package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionFactory;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.Attempt.Status;
import com.harshal.quizforge.quiz.QuizDtos.AttemptSession;
import com.harshal.quizforge.quiz.QuizDtos.SaveAnswerRequest;
import com.harshal.quizforge.quiz.QuizSettings.ResultsVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttemptServiceTest {

    private final QuestionTypeRegistry registry = QuizServiceTest.registry();
    private final QuestionMapper mapper = new QuestionMapper(registry);
    private final CurrentUser learner = new CurrentUser("learner@test.com", Set.of());

    private AttemptRepository attempts;
    private QuizService quizService;
    private AttemptService service;
    private Quiz quiz;
    private Attempt saved;   // what the fake repository holds

    @BeforeEach
    void setUp() {
        // A published public quiz: true/false (1 pt) + multi-select (2 pts) + single choice (1 pt)
        var f = new QuestionFactory(new Random(3), false);
        User teacher = new User();
        teacher.setEmail("teacher@test.com");
        quiz = new Quiz();
        quiz.setId(10L);
        quiz.setTitle("Quiz");
        quiz.setOwner(teacher);
        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setVisibility(Visibility.PUBLIC);
        AtomicLong ids = new AtomicLong(100);
        List.of(f.trueFalse(Difficulty.EASY, "Water is wet", true, "x"),
                f.multipleChoice(Difficulty.EASY, "Primes?", List.of("2", "4", "5", "9"), List.of(0, 2), "x"),
                f.singleChoice(Difficulty.EASY, "Capital?", List.of("Rome", "Paris", "Oslo"), 1, "x"))
                .forEach(d -> { Question q = mapper.toEntity(d); q.setId(ids.incrementAndGet()); quiz.addQuestion(q); });

        attempts = mock(AttemptRepository.class);
        when(attempts.saveAndFlush(any())).thenAnswer(inv -> { saved = inv.getArgument(0); saved.setId(1L); return saved; });
        when(attempts.findWithAnswersById(1L)).thenAnswer(inv -> Optional.ofNullable(saved));
        when(attempts.findFirstByQuizIdAndUserEmailAndStatus(eq(10L), eq("learner@test.com"), eq(Status.IN_PROGRESS)))
                .thenAnswer(inv -> Optional.ofNullable(saved).filter(Attempt::isOpen));

        quizService = mock(QuizService.class);
        when(quizService.getTakeable(any(), eq(10L))).thenReturn(quiz);
        when(quizService.attemptsLeft(any(), any())).thenReturn(null);

        UserRepository users = mock(UserRepository.class);
        User learnerUser = new User();
        learnerUser.setEmail("learner@test.com");
        when(users.findByEmail("learner@test.com")).thenReturn(Optional.of(learnerUser));

        service = new AttemptService(attempts, quizService, registry, mapper, users,
                Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC));
    }

    private Long qid(int index) {
        return quiz.getQuestions().get(index).getId();
    }

    @Test
    void startCreatesAttemptAndResumeReturnsTheSameOneWithSavedAnswers() {
        AttemptSession first = service.start(learner, 10L);
        assertThat(first.questions()).hasSize(3);
        assertThat(first.answers()).isEmpty();

        service.saveAnswer(learner, 1L, qid(0), new SaveAnswerRequest(Map.of("value", true), true));
        AttemptSession resumed = service.start(learner, 10L);

        assertThat(resumed.attemptId()).isEqualTo(first.attemptId());
        assertThat(resumed.answers()).singleElement().satisfies(a -> {
            assertThat(a.questionId()).isEqualTo(qid(0));
            assertThat(a.flagged()).isTrue();
            assertThat(a.response()).containsEntry("value", true);
        });
        verify(attempts, times(1)).saveAndFlush(any());
    }

    @Test
    void autosaveOverwritesTheSameQuestionRow() {
        service.start(learner, 10L);
        service.saveAnswer(learner, 1L, qid(2), new SaveAnswerRequest(Map.of("selected", 0), false));
        service.saveAnswer(learner, 1L, qid(2), new SaveAnswerRequest(Map.of("selected", 1), false));

        assertThat(saved.getAnswers()).hasSize(1);
        assertThat(saved.getAnswers().getFirst().getResponseJson()).contains("1");
    }

    @Test
    void submitGradesSavedAnswersWithPartialCreditAndPassMark() {
        quiz.getSettings().setPassPercentage(60);
        service.start(learner, 10L);
        service.saveAnswer(learner, 1L, qid(0), new SaveAnswerRequest(Map.of("value", true), false));      // 1/1
        service.saveAnswer(learner, 1L, qid(1), new SaveAnswerRequest(Map.of("selected", List.of(0)), false)); // 1/2
        // question 3 left unanswered → 0/1

        var result = service.submit(learner, 1L);

        assertThat(result.status()).isEqualTo(Status.SUBMITTED);
        assertThat(result.score()).isEqualTo(2.0);
        assertThat(result.maxScore()).isEqualTo(4.0);
        assertThat(result.percentage()).isEqualTo(50);
        assertThat(result.passed()).isFalse();
        assertThat(result.results()).hasSize(3);
        assertThat(saved.getAnswers()).hasSize(3);   // the unanswered question is recorded too
    }

    @Test
    void submitIsIdempotentAndSavingAfterSubmitIsRejected() {
        service.start(learner, 10L);
        service.saveAnswer(learner, 1L, qid(0), new SaveAnswerRequest(Map.of("value", true), false));
        var first = service.submit(learner, 1L);
        var again = service.submit(learner, 1L);

        assertThat(again.score()).isEqualTo(first.score());
        assertThatThrownBy(() -> service.saveAnswer(learner, 1L, qid(0), new SaveAnswerRequest(Map.of("value", false), false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void scoreOnlyQuizzesHideDetailsFromLearners() {
        quiz.getSettings().setResultsVisibility(ResultsVisibility.SCORE_ONLY);
        service.start(learner, 10L);
        var result = service.submit(learner, 1L);

        assertThat(result.detailsHidden()).isTrue();
        assertThat(result.results()).isEmpty();
    }

    @Test
    void noAttemptsLeftIsAConflict() {
        when(quizService.attemptsLeft(any(), any())).thenReturn(0);
        quiz.getSettings().setMaxAttempts(2);

        assertThatThrownBy(() -> service.start(learner, 10L)).isInstanceOf(ConflictException.class).hasMessageContaining("2 attempts");
    }

    @Test
    void omittedFlagMeansNotFlagged() {
        service.start(learner, 10L);
        service.saveAnswer(learner, 1L, qid(0), new SaveAnswerRequest(Map.of("value", true), null));
        assertThat(saved.getAnswers().getFirst().isFlagged()).isFalse();
    }

    @Test
    void shuffledOrderIsStableForTheSameAttempt() {
        quiz.getSettings().setShuffleQuestions(true);
        quiz.getSettings().setShuffleOptions(true);
        AttemptSession a = service.start(learner, 10L);
        AttemptSession b = service.start(learner, 10L);   // resume

        assertThat(b.questions()).extracting(QuizDtos.SessionQuestion::id).isEqualTo(a.questions().stream().map(QuizDtos.SessionQuestion::id).toList());
        var choice = a.questions().stream().filter(q -> q.optionOrder() != null).findFirst().orElseThrow();
        assertThat(choice.optionOrder()).containsExactlyInAnyOrderElementsOf(List.of(0, 1, 2, 3).subList(0, choice.optionOrder().size()));
    }
}

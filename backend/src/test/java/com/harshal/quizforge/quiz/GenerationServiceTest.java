package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.AiGenerationException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionFactory;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuiz;
import com.harshal.quizforge.quiz.GenerationJob.Status;
import com.harshal.quizforge.quiz.GenerationService.JobRequest;
import com.harshal.quizforge.quiz.QuizDtos.Blueprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs jobs synchronously (direct executor) with an in-memory job store and a fake AI. */
class GenerationServiceTest {

    private final Map<UUID, GenerationJob> store = new HashMap<>();
    private GenerationJobRepository jobs;
    private AiQuizGenerator ai;
    private QuizService quizzes;
    private GenerationService service;
    private final CurrentUser me = new CurrentUser("me@test.com", Set.of());
    private final JobRequest request = new JobRequest(null, "x".repeat(300),
            new Blueprint(Map.of(QuestionType.TRUE_FALSE, 2, QuestionType.NUMERIC, 1), Difficulty.EASY));

    @BeforeEach
    void setUp() {
        jobs = mock(GenerationJobRepository.class);
        when(jobs.save(any())).thenAnswer(inv -> { GenerationJob j = inv.getArgument(0); store.put(j.getId(), j); return j; });
        when(jobs.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<UUID>getArgument(0))));
        when(jobs.findOwnerEmail(any())).thenReturn("me@test.com");
        doAnswer(inv -> { store.get(inv.<UUID>getArgument(0)).setDoneSteps(inv.getArgument(1)); return null; })
                .when(jobs).updateProgress(any(), anyInt());

        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setEmail("me@test.com");
        when(users.findByEmail("me@test.com")).thenReturn(Optional.of(user));

        ai = mock(AiQuizGenerator.class);
        quizzes = mock(QuizService.class);
        service = new GenerationService(jobs, users, ai, quizzes, mock(StudioService.class), QuizServiceTest.registry(),
                Runnable::run, Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void successfulJobCreatesPracticeQuizAndReportsProgress() {
        var f = new QuestionFactory(new Random(1));
        var drafts = List.of(f.trueFalse(Difficulty.EASY, "S1", true, "x"), f.trueFalse(Difficulty.EASY, "S2", false, "x"));
        when(ai.generate(anyString(), anyMap(), eq(Difficulty.EASY), any(IntConsumer.class))).thenAnswer(inv -> {
            IntConsumer progress = inv.getArgument(3);
            progress.accept(1);
            progress.accept(2);
            return new GeneratedQuiz("AI title", drafts);
        });
        Quiz saved = new Quiz();
        saved.setId(42L);
        when(quizzes.createPracticeQuiz(eq("me@test.com"), isNull(), eq("AI title"), anyString(), eq(drafts))).thenReturn(saved);

        var started = service.startPracticeQuiz(me, request);
        GenerationJob job = store.get(started.id());

        assertThat(started.totalSteps()).isEqualTo(2);   // two question types → two AI calls
        assertThat(job.getStatus()).isEqualTo(Status.SUCCEEDED);
        assertThat(job.getQuizId()).isEqualTo(42L);
        assertThat(job.getQuestionCount()).isEqualTo(2);
        assertThat(job.getDoneSteps()).isEqualTo(2);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void failedJobStoresTheErrorAndSavesNothing() {
        when(ai.generate(anyString(), anyMap(), any(), any(IntConsumer.class))).thenThrow(new AiGenerationException("model offline"));

        var started = service.startPracticeQuiz(me, request);

        GenerationJob job = store.get(started.id());
        assertThat(job.getStatus()).isEqualTo(Status.FAILED);
        assertThat(job.getError()).contains("model offline");
        verifyNoInteractions(quizzes);
    }

    @Test
    void onlyOneRunningJobPerUser() {
        when(jobs.existsByUserEmailAndStatusIn(eq("me@test.com"), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.startPracticeQuiz(me, request)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(ai);
    }
}

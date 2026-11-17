package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuiz;
import com.harshal.quizforge.quiz.GenerationJob.Status;
import com.harshal.quizforge.quiz.GenerationJob.Target;
import com.harshal.quizforge.quiz.QuizDtos.Blueprint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Runs AI quiz generation in the background. Mixed blueprints take minutes with local models,
 * longer than an HTTP request (or a proxy timeout) should be held open; the client starts a job,
 * then polls its progress.
 */
@Slf4j
@Service
public class GenerationService {

    public record JobView(UUID id, Status status, Target target, int totalSteps, int doneSteps, int questionCount,
                          Long quizId, String error) {}

    /** What a job needs to run, stored as JSON with the job. */
    public record JobRequest(String title, String sourceText, Blueprint blueprint) {}

    private final GenerationJobRepository jobs;
    private final UserRepository users;
    private final AiQuizGenerator ai;
    private final QuizService quizzes;
    private final StudioService studio;
    private final QuestionTypeRegistry json;
    private final Executor executor;
    private final Clock clock;
    private final GenerationRateLimiter rateLimiter;

    public GenerationService(GenerationJobRepository jobs, UserRepository users, AiQuizGenerator ai, QuizService quizzes,
                             StudioService studio, QuestionTypeRegistry json,
                             @Qualifier("generationExecutor") Executor executor, Clock clock, GenerationRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.jobs = jobs;
        this.users = users;
        this.ai = ai;
        this.quizzes = quizzes;
        this.studio = studio;
        this.json = json;
        this.executor = executor;
        this.clock = clock;
    }

    /** Learner: generate a new private practice quiz. */
    public JobView startPracticeQuiz(CurrentUser user, JobRequest request) {
        return start(user, request, Target.NEW_PRACTICE_QUIZ, null);
    }

    /** Instructor: add generated questions to a Studio draft. */
    public JobView startAppend(CurrentUser user, Long quizId, JobRequest request) {
        studio.checkCanAppend(user, quizId);   // owner/author + still a draft
        return start(user, request, Target.APPEND_TO_QUIZ, quizId);
    }

    private JobView start(CurrentUser user, JobRequest request, Target target, Long quizId) {
        var counts = QuizService.checkedCounts(request.blueprint().counts());
        // One job at a time per user: protects the (single, slow) model from being flooded.
        if (jobs.existsByUserEmailAndStatusIn(user.email(), EnumSet.of(Status.QUEUED, Status.RUNNING))) {
            throw new ConflictException("You already have a quiz being generated. Wait for it to finish.");
        }
        rateLimiter.acquire(user.email());   // N per hour per user → 429 with Retry-After
        GenerationJob job = new GenerationJob();
        job.setId(UUID.randomUUID());
        job.setUser(users.findByEmail(user.email()).orElseThrow(() -> new NotFoundException("User not found")));
        job.setTarget(target);
        job.setQuizId(quizId);
        job.setStatus(Status.QUEUED);
        job.setRequestJson(json.toJson(new JobRequest(request.title(), request.sourceText(),
                new Blueprint(counts, request.blueprint().difficulty()))));
        job.setTotalSteps(AiQuizGenerator.stepsFor(counts));
        job.setCreatedAt(clock.instant());
        jobs.save(job);   // committed before the worker starts, so the worker always finds it

        UUID id = job.getId();
        executor.execute(() -> run(id));
        return view(job);
    }

    /** Worker body. Each step commits on its own so the polling client sees progress. */
    void run(UUID jobId) {
        GenerationJob job = jobs.findById(jobId).orElseThrow();
        job.setStatus(Status.RUNNING);
        jobs.save(job);
        try {
            JobRequest req = json.read(job.getRequestJson(), JobRequest.class);
            GeneratedQuiz generated = ai.generate(req.sourceText(), req.blueprint().counts(), req.blueprint().difficulty(),
                    done -> jobs.updateProgress(jobId, done));

            String ownerEmail = jobs.findOwnerEmail(jobId);
            if (job.getTarget() == Target.NEW_PRACTICE_QUIZ) {
                Quiz quiz = quizzes.createPracticeQuiz(ownerEmail, req.title(), generated.title(), req.sourceText(), generated.questions());
                job.setQuizId(quiz.getId());
            } else {
                quizzes.appendToDraft(job.getQuizId(), generated.questions());
            }
            job.setQuestionCount(generated.questions().size());
            job.setDoneSteps(job.getTotalSteps());
            job.setStatus(Status.SUCCEEDED);
        } catch (RuntimeException e) {
            log.warn("Generation job {} failed: {}", jobId, e.getMessage());
            job.setStatus(Status.FAILED);
            job.setError(e.getMessage() == null ? "Generation failed" : e.getMessage());
        }
        job.setFinishedAt(clock.instant());
        jobs.save(job);
    }

    @Transactional(readOnly = true)
    public JobView get(CurrentUser user, UUID id) {
        GenerationJob job = jobs.findById(id)
                .filter(j -> j.getUser().getEmail().equals(user.email()))
                .orElseThrow(() -> new NotFoundException("Job not found"));
        return view(job);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failJobsInterruptedByRestart() {
        int n = jobs.failUnfinished("Interrupted by a server restart. Please try again.", clock.instant());
        if (n > 0) log.info("Marked {} interrupted generation job(s) as failed", n);
    }

    private static JobView view(GenerationJob j) {
        return new JobView(j.getId(), j.getStatus(), j.getTarget(), j.getTotalSteps(), j.getDoneSteps(),
                j.getQuestionCount(), j.getQuizId(), j.getError());
    }
}

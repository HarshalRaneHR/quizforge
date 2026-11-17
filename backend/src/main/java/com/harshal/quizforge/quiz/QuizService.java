package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.ForbiddenException;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.question.QuestionDraft;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.quiz.QuizDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.harshal.quizforge.quiz.QuizRepository.Specs.*;
import static org.springframework.util.StringUtils.hasText;

/** Learner-facing quiz operations: my quizzes, the Explore catalog, taking and deleting. */
@Service
@RequiredArgsConstructor
public class QuizService {

    public static final int MAX_QUESTIONS = 20;

    private final QuizRepository quizRepo;
    private final UserRepository userRepo;
    private final AttemptRepository attemptRepo;
    private final QuestionMapper questions;

    /** Saves AI-generated questions as a personal practice quiz (private, ready to take). */
    @Transactional
    public Quiz createPracticeQuiz(String ownerEmail, String title, String fallbackTitle, String sourceText, List<QuestionDraft> drafts) {
        User owner = userRepo.findByEmail(ownerEmail).orElseThrow(() -> new NotFoundException("User not found"));
        Quiz quiz = new Quiz();
        quiz.setTitle(hasText(title) ? title.trim() : fallbackTitle);
        quiz.setSourceText(sourceText);
        quiz.setOwner(owner);
        quiz.setVisibility(Visibility.PRIVATE);
        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setPublishedAt(Instant.now());
        drafts.forEach(d -> quiz.addQuestion(questions.toEntity(d)));
        return quizRepo.save(quiz);
    }

    /**
     * Adds generated questions to a Studio draft. Called from the background generation job (no
     * logged-in user on that thread), so the job checked access before it started.
     */
    @Transactional
    public int appendToDraft(Long quizId, List<QuestionDraft> drafts) {
        Quiz quiz = load(quizId);
        if (!quiz.isDraft()) throw new ConflictException("Quiz was published while questions were being generated");
        drafts.forEach(d -> quiz.addQuestion(questions.toEntity(d)));
        return drafts.size();
    }

    /** Quizzes I own, optionally filtered (Home shows PRIVATE practice quizzes, Studio shows PUBLIC ones). */
    @Transactional(readOnly = true)
    public Page<QuizSummary> listMine(CurrentUser user, Visibility visibility, QuizStatus status, Pageable pageable) {
        return quizRepo.findAll(ownedBy(user.email()).and(hasVisibility(visibility)).and(hasStatus(status)), pageable)
                .map(QuizService::toSummary);
    }

    /** Published public quizzes anyone can take. */
    @Transactional(readOnly = true)
    public Page<CatalogQuiz> explore(String search, Pageable pageable) {
        return quizRepo.findAll(listed().and(titleContains(search)), pageable)
                .map(q -> new CatalogQuiz(q.getId(), q.getTitle(), q.getDescription(), q.getOwner().getEmail(),
                        q.getQuestionCount(), q.getTotalPoints(), q.getPublishedAt()));
    }

    /** Landing page before starting: settings and my attempts so far (no questions). */
    @Transactional(readOnly = true)
    public QuizDetail getOverview(CurrentUser user, Long id) {
        Quiz quiz = getTakeable(user, id);
        long used = attemptRepo.countByQuizIdAndUserEmailAndStatus(id, user.email(), Attempt.Status.SUBMITTED);
        Long openId = attemptRepo.findFirstByQuizIdAndUserEmailAndStatus(id, user.email(), Attempt.Status.IN_PROGRESS)
                .map(Attempt::getId).orElse(null);
        return new QuizDetail(quiz.getId(), quiz.getTitle(), quiz.getDescription(), quiz.getStatus(),
                quiz.getOwner().getEmail(), quiz.getQuestionCount(), quiz.getTotalPoints(), Settings.of(quiz.getSettings()),
                (int) used, attemptsLeft(user, quiz), openId, QuizPolicy.canAuthor(quiz, user));
    }

    /** Remaining attempts; null = unlimited. Authors previewing their own quiz are never limited. */
    Integer attemptsLeft(CurrentUser user, Quiz quiz) {
        Integer max = quiz.getSettings().getMaxAttempts();
        if (max == null || QuizPolicy.canAuthor(quiz, user)) return null;
        long used = attemptRepo.countByQuizIdAndUserEmailAndStatus(quiz.getId(), user.email(), Attempt.Status.SUBMITTED);
        return (int) Math.max(0, max - used);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        Quiz quiz = load(id);
        if (!QuizPolicy.canTake(quiz, user)) throw new NotFoundException("Quiz not found");
        if (!QuizPolicy.canDelete(quiz, user)) throw new ForbiddenException("Only the owner can delete this quiz");
        boolean othersAttempted = attemptRepo.existsByQuizIdAndUserEmailNot(id, quiz.getOwner().getEmail());
        if (othersAttempted && !user.can(com.harshal.quizforge.auth.Permission.QUIZ_MANAGE_ALL)) {
            throw new ConflictException("Learners have attempted this quiz; archive it instead of deleting");
        }
        attemptRepo.deleteByQuizId(id);   // attempts reference the quiz (FK)
        quizRepo.delete(quiz);
    }

    /** Callers must already be inside a transaction. 404 (not 403) for quizzes you can't see, so IDs can't be probed. */
    Quiz getTakeable(CurrentUser user, Long id) {
        Quiz quiz = load(id);
        if (!QuizPolicy.canTake(quiz, user)) throw new NotFoundException("Quiz not found");
        return quiz;
    }

    Quiz load(Long id) {
        return quizRepo.findWithQuestionsById(id).orElseThrow(() -> new NotFoundException("Quiz not found"));
    }

    static Map<QuestionType, Integer> checkedCounts(Map<QuestionType, Integer> requested) {
        Map<QuestionType, Integer> counts = new EnumMap<>(QuestionType.class);
        requested.forEach((type, n) -> { if (type != null && n != null && n > 0) counts.put(type, n); });
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total < 1 || total > MAX_QUESTIONS) {
            throw new BadRequestException("Choose between 1 and " + MAX_QUESTIONS + " questions in total (got " + total + ")");
        }
        return counts;
    }

    static QuizSummary toSummary(Quiz q) {
        return new QuizSummary(q.getId(), q.getTitle(), q.getStatus(), q.getVisibility(), q.getQuestionCount(),
                q.getTotalPoints(), q.getCreatedAt(), q.getUpdatedAt());
    }
}

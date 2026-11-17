package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.ForbiddenException;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.question.QuestionDraft;
import com.harshal.quizforge.quiz.StudioDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

/**
 * Quiz authoring for instructors: create drafts, edit questions, publish, archive, duplicate.
 * Questions can only change while a quiz is a DRAFT, so published quizzes (and the attempts
 * learners made on them) never change underneath anyone.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('quiz:author')")
public class StudioService {

    private final QuizRepository quizRepo;
    private final UserRepository userRepo;
    private final AttemptRepository attemptRepo;
    private final QuestionMapper questions;
    private final AuthoringCodec codec;

    @Transactional
    public StudioQuiz create(CurrentUser user, CreateQuizRequest req) {
        User owner = userRepo.findByEmail(user.email()).orElseThrow(() -> new NotFoundException("User not found"));
        Quiz quiz = new Quiz();
        quiz.setTitle(req.title().trim());
        quiz.setDescription(hasText(req.description()) ? req.description().trim() : null);
        quiz.setOwner(owner);
        quiz.setVisibility(Visibility.PUBLIC);
        quiz.setStatus(QuizStatus.DRAFT);
        return view(quizRepo.save(quiz));
    }

    @Transactional(readOnly = true)
    public StudioQuiz get(CurrentUser user, Long id) {
        return view(editable(user, id));
    }

    @Transactional
    public StudioQuiz update(CurrentUser user, Long id, UpdateQuizRequest req) {
        Quiz quiz = editable(user, id);
        quiz.setTitle(req.title().trim());
        quiz.setDescription(hasText(req.description()) ? req.description().trim() : null);
        quiz.setUpdatedAt(Instant.now());
        return view(quiz);
    }

    // ---------------------------------------------------------------- questions (DRAFT only)

    @Transactional
    public AuthoringQuestionView addQuestion(CurrentUser user, Long id, AuthoringQuestion input) {
        Quiz quiz = draft(user, id);
        QuestionDraft draft = codec.toDraft(input);   // validates; 400 on problems
        Question q = questions.toEntity(draft);
        quiz.addQuestion(q);
        quizRepo.flush();   // assigns the id
        return new AuthoringQuestionView(q.getId(), q.getPosition(), codec.toAuthoring(q));
    }

    @Transactional
    public AuthoringQuestionView updateQuestion(CurrentUser user, Long id, Long questionId, AuthoringQuestion input) {
        Quiz quiz = draft(user, id);
        Question q = question(quiz, questionId);
        questions.apply(codec.toDraft(input), q);
        quiz.recalculate();
        return new AuthoringQuestionView(q.getId(), q.getPosition(), codec.toAuthoring(q));
    }

    @Transactional
    public void deleteQuestion(CurrentUser user, Long id, Long questionId) {
        Quiz quiz = draft(user, id);
        quiz.removeQuestion(question(quiz, questionId));
    }

    @Transactional
    public StudioQuiz reorder(CurrentUser user, Long id, ReorderRequest req) {
        Quiz quiz = draft(user, id);
        Map<Long, Question> byId = quiz.getQuestions().stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        if (req.questionIds().size() != byId.size() || !new HashSet<>(req.questionIds()).equals(byId.keySet())) {
            throw new BadRequestException("questionIds must list every question of the quiz exactly once");
        }
        quiz.getQuestions().clear();
        req.questionIds().forEach(qid -> quiz.getQuestions().add(byId.get(qid)));
        quiz.recalculate();
        return view(quiz);
    }

    /** Pre-flight check before starting a generation job for this quiz. */
    @Transactional(readOnly = true)
    public void checkCanAppend(CurrentUser user, Long id) {
        draft(user, id);
    }

    // ---------------------------------------------------------------- lifecycle

    @Transactional
    public StudioQuiz publish(CurrentUser user, Long id) {
        Quiz quiz = draft(user, id);
        if (quiz.getQuestions().isEmpty()) throw new BadRequestException("Add at least one question before publishing");
        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setPublishedAt(Instant.now());
        quiz.setUpdatedAt(Instant.now());
        return view(quiz);
    }

    /** Back to draft for editing — only while no learner has attempted it (their results must stay valid). */
    @Transactional
    public StudioQuiz unpublish(CurrentUser user, Long id) {
        Quiz quiz = inStatus(editable(user, id), QuizStatus.PUBLISHED);
        if (hasLearnerAttempts(quiz)) {
            throw new ConflictException("Learners have already taken this quiz. Duplicate it to make changes.");
        }
        quiz.setStatus(QuizStatus.DRAFT);
        quiz.setUpdatedAt(Instant.now());
        return view(quiz);
    }

    @Transactional
    public StudioQuiz archive(CurrentUser user, Long id) {
        Quiz quiz = inStatus(editable(user, id), QuizStatus.PUBLISHED);
        quiz.setStatus(QuizStatus.ARCHIVED);
        quiz.setUpdatedAt(Instant.now());
        return view(quiz);
    }

    @Transactional
    public StudioQuiz restore(CurrentUser user, Long id) {
        Quiz quiz = inStatus(editable(user, id), QuizStatus.ARCHIVED);
        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setUpdatedAt(Instant.now());
        return view(quiz);
    }

    /** New editable draft with copies of all questions — the way to change a quiz learners have taken. */
    @Transactional
    public StudioQuiz duplicate(CurrentUser user, Long id) {
        Quiz source = editable(user, id);
        User owner = userRepo.findByEmail(user.email()).orElseThrow(() -> new NotFoundException("User not found"));
        Quiz copy = new Quiz();
        String title = "Copy of " + source.getTitle();
        copy.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
        copy.setDescription(source.getDescription());
        copy.setOwner(owner);
        copy.setVisibility(Visibility.PUBLIC);
        copy.setStatus(QuizStatus.DRAFT);
        source.getQuestions().forEach(q -> copy.addQuestion(questions.copy(q)));
        return view(quizRepo.save(copy));
    }

    // ---------------------------------------------------------------- helpers

    private Quiz editable(CurrentUser user, Long id) {
        Quiz quiz = quizRepo.findWithQuestionsById(id).orElseThrow(() -> new NotFoundException("Quiz not found"));
        if (!QuizPolicy.canTake(quiz, user)) throw new NotFoundException("Quiz not found");
        if (!QuizPolicy.canAuthor(quiz, user)) throw new ForbiddenException("You can't edit this quiz");
        if (quiz.getVisibility() != Visibility.PUBLIC) throw new BadRequestException("Practice quizzes can't be edited in the Studio");
        return quiz;
    }

    private Quiz draft(CurrentUser user, Long id) {
        return inStatus(editable(user, id), QuizStatus.DRAFT);
    }

    private static Quiz inStatus(Quiz quiz, QuizStatus expected) {
        if (quiz.getStatus() != expected) {
            throw new ConflictException("Quiz is " + quiz.getStatus() + "; this needs it to be " + expected);
        }
        return quiz;
    }

    private static Question question(Quiz quiz, Long questionId) {
        return quiz.getQuestions().stream().filter(q -> q.getId().equals(questionId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Question not found in this quiz"));
    }

    private boolean hasLearnerAttempts(Quiz quiz) {
        return attemptRepo.existsByQuizIdAndUserEmailNot(quiz.getId(), quiz.getOwner().getEmail());
    }

    private StudioQuiz view(Quiz q) {
        List<AuthoringQuestionView> qs = q.getQuestions().stream()
                .map(question -> new AuthoringQuestionView(question.getId(), question.getPosition(), codec.toAuthoring(question)))
                .toList();
        return new StudioQuiz(q.getId(), q.getTitle(), q.getDescription(), q.getStatus(), q.getVisibility(),
                q.getQuestionCount(), q.getTotalPoints(), q.getCreatedAt(), q.getUpdatedAt(), q.getPublishedAt(),
                q.getId() != null && hasLearnerAttempts(q), qs);
    }
}

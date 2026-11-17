package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.question.QuestionDraft;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuiz;
import com.harshal.quizforge.quiz.QuizDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class QuizService {

    public static final int MAX_QUESTIONS = 20;

    private final QuizRepository quizRepo;
    private final UserRepository userRepo;
    private final AttemptRepository attemptRepo;
    private final AiQuizGenerator ai;
    private final QuestionMapper questions;

    // Deliberately NOT @Transactional: never hold a DB connection while waiting 10-60s on an LLM.
    public QuizSummary generate(String email, GenerateQuizRequest req) {
        Map<QuestionType, Integer> counts = checkedCounts(req.blueprint().counts());
        GeneratedQuiz generated = ai.generate(req.sourceText(), counts, req.blueprint().difficulty());
        User owner = userRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Quiz quiz = new Quiz();
        quiz.setTitle(hasText(req.title()) ? req.title().trim() : generated.title());
        quiz.setSourceText(req.sourceText());
        quiz.setOwner(owner);
        for (QuestionDraft draft : generated.questions()) {
            quiz.addQuestion(questions.toEntity(draft));
        }
        return toSummary(quizRepo.save(quiz));   // save() runs in its own transaction; questions cascade
    }

    @Transactional(readOnly = true)
    public Page<QuizSummary> list(String email, Pageable pageable) {
        return quizRepo.findByOwnerEmail(email, pageable).map(QuizService::toSummary);
    }

    @Transactional(readOnly = true)
    public QuizDetail getForTaking(String email, Long id) {
        Quiz quiz = getOwnedQuiz(email, id);
        List<QuestionView> views = quiz.getQuestions().stream().map(questions::toView).toList();
        return new QuizDetail(quiz.getId(), quiz.getTitle(), quiz.getTotalPoints(), views);
    }

    @Transactional
    public void delete(String email, Long id, boolean canManageAll) {
        Quiz quiz = canManageAll
                ? quizRepo.findById(id).orElseThrow(() -> new NotFoundException("Quiz not found"))
                : getOwnedQuiz(email, id);
        attemptRepo.deleteByQuizId(quiz.getId());   // attempts reference the quiz (FK)
        quizRepo.delete(quiz);
    }

    /** Callers must already be inside a transaction. */
    Quiz getOwnedQuiz(String email, Long id) {
        Quiz quiz = quizRepo.findWithQuestionsById(id)
                .orElseThrow(() -> new NotFoundException("Quiz not found"));
        if (!quiz.getOwner().getEmail().equals(email)) {
            // 404 instead of 403 so users can't probe which quiz IDs exist.
            throw new NotFoundException("Quiz not found");
        }
        return quiz;
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

    private static QuizSummary toSummary(Quiz q) {
        return new QuizSummary(q.getId(), q.getTitle(), q.getQuestionCount(), q.getTotalPoints(), q.getCreatedAt());
    }
}

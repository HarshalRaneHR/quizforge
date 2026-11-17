package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuestion;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuiz;
import com.harshal.quizforge.quiz.QuizDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepo;
    private final UserRepository userRepo;
    private final AttemptRepository attemptRepo;
    private final AiQuizGenerator ai;

    // Deliberately NOT @Transactional: never hold a DB connection while waiting 10-30s on an LLM.
    public QuizSummary generate(String email, GenerateQuizRequest req) {
        GeneratedQuiz generated = ai.generate(req.sourceText(), req.questionCount());
        User owner = userRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Quiz quiz = new Quiz();
        quiz.setTitle(hasText(req.title()) ? req.title().trim() : generated.title());
        quiz.setSourceText(req.sourceText());
        quiz.setOwner(owner);
        for (GeneratedQuestion g : generated.questions()) {
            Question q = new Question();
            q.setPrompt(g.prompt());
            q.setOptions(new ArrayList<>(g.options()));
            q.setCorrectIndex(g.correctIndex());
            q.setExplanation(g.explanation());
            quiz.addQuestion(q);
        }
        quiz.setQuestionCount(quiz.getQuestions().size());
        return toSummary(quizRepo.save(quiz));   // save() runs in its own transaction; questions cascade
    }

    @Transactional(readOnly = true)
    public Page<QuizSummary> list(String email, Pageable pageable) {
        return quizRepo.findByOwnerEmail(email, pageable).map(QuizService::toSummary);
    }

    @Transactional(readOnly = true)
    public QuizDetail getForTaking(String email, Long id) {
        Quiz quiz = getOwnedQuiz(email, id);
        List<QuestionView> questions = quiz.getQuestions().stream()
                .map(q -> new QuestionView(q.getId(), q.getPrompt(), List.copyOf(q.getOptions())))
                .toList();
        return new QuizDetail(quiz.getId(), quiz.getTitle(), questions);
    }

    @Transactional
    public void delete(String email, Long id, boolean isAdmin) {
        Quiz quiz = isAdmin
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

    private static QuizSummary toSummary(Quiz q) {
        return new QuizSummary(q.getId(), q.getTitle(), q.getQuestionCount(), q.getCreatedAt());
    }
}

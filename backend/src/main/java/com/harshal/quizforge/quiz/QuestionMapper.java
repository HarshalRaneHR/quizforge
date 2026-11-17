package com.harshal.quizforge.quiz;

import com.harshal.quizforge.question.QuestionDraft;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.QuizDtos.QuestionView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts between question drafts, JPA entities and API views. */
@Component
@RequiredArgsConstructor
public class QuestionMapper {

    private final QuestionTypeRegistry registry;

    public Question toEntity(QuestionDraft draft) {
        Question q = new Question();
        q.setType(draft.type());
        q.setDifficulty(draft.difficulty());
        q.setPoints(draft.points());
        q.setPrompt(draft.prompt());
        q.setContentJson(registry.toJson(draft.content()));
        q.setAnswerKeyJson(registry.toJson(draft.answerKey()));
        q.setExplanation(draft.explanation());
        return q;
    }

    /** Learner-safe view: content only, never the answer key. */
    public QuestionView toView(Question q) {
        return new QuestionView(q.getId(), q.getType(), q.getDifficulty(), q.getPoints(), q.getPrompt(),
                registry.toMap(q.getContentJson()));
    }
}

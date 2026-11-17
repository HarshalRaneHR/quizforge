package com.harshal.quizforge.quiz;

import com.harshal.quizforge.question.QuestionDraft;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.QuizDtos.SessionQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Converts between question drafts, JPA entities and API views. */
@Component
@RequiredArgsConstructor
public class QuestionMapper {

    private final QuestionTypeRegistry registry;

    public Question toEntity(QuestionDraft draft) {
        Question q = new Question();
        apply(draft, q);
        return q;
    }

    /** Overwrites an existing question with a draft (used when editing). */
    public void apply(QuestionDraft draft, Question q) {
        q.setType(draft.type());
        q.setDifficulty(draft.difficulty());
        q.setPoints(draft.points());
        q.setPrompt(draft.prompt());
        q.setContentJson(registry.toJson(draft.content()));
        q.setAnswerKeyJson(registry.toJson(draft.answerKey()));
        q.setExplanation(draft.explanation());
    }

    /** Copy for "duplicate quiz". */
    public Question copy(Question source) {
        Question q = new Question();
        q.setType(source.getType());
        q.setDifficulty(source.getDifficulty());
        q.setPoints(source.getPoints());
        q.setPrompt(source.getPrompt());
        q.setContentJson(source.getContentJson());
        q.setAnswerKeyJson(source.getAnswerKeyJson());
        q.setExplanation(source.getExplanation());
        return q;
    }

    /** Learner-safe view: content only, never the answer key. */
    public SessionQuestion toSessionQuestion(Question q, List<Integer> optionOrder) {
        return new SessionQuestion(q.getId(), q.getType(), q.getDifficulty(), q.getPoints(), q.getPrompt(),
                registry.toMap(q.getContentJson()), optionOrder);
    }
}

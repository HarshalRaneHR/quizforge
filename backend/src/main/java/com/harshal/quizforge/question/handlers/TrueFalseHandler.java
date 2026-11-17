package com.harshal.quizforge.question.handlers;

import com.harshal.quizforge.question.AnswerKey;
import com.harshal.quizforge.question.LearnerResponse;
import com.harshal.quizforge.question.QuestionContent;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class TrueFalseHandler
        implements QuestionTypeHandler<QuestionContent.TrueFalse, AnswerKey.TrueFalse, LearnerResponse.TrueFalse> {

    @Override public QuestionType type() { return QuestionType.TRUE_FALSE; }
    @Override public Class<QuestionContent.TrueFalse> contentClass() { return QuestionContent.TrueFalse.class; }
    @Override public Class<AnswerKey.TrueFalse> keyClass() { return AnswerKey.TrueFalse.class; }
    @Override public Class<LearnerResponse.TrueFalse> responseClass() { return LearnerResponse.TrueFalse.class; }

    @Override
    public List<String> validate(String prompt, QuestionContent.TrueFalse content, AnswerKey.TrueFalse key) {
        return key.answer() == null ? List.of("answer must be true or false") : List.of();
    }

    @Override
    public double grade(QuestionContent.TrueFalse content, AnswerKey.TrueFalse key, LearnerResponse.TrueFalse response) {
        return Objects.equals(response.value(), key.answer()) ? 1.0 : 0.0;
    }
}

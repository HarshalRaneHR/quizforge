package com.harshal.quizforge.question.handlers;

import com.harshal.quizforge.question.AnswerKey;
import com.harshal.quizforge.question.LearnerResponse;
import com.harshal.quizforge.question.QuestionContent;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SingleChoiceHandler
        implements QuestionTypeHandler<QuestionContent.Choice, AnswerKey.SingleChoice, LearnerResponse.SingleChoice> {

    @Override public QuestionType type() { return QuestionType.SINGLE_CHOICE; }
    @Override public Class<QuestionContent.Choice> contentClass() { return QuestionContent.Choice.class; }
    @Override public Class<AnswerKey.SingleChoice> keyClass() { return AnswerKey.SingleChoice.class; }
    @Override public Class<LearnerResponse.SingleChoice> responseClass() { return LearnerResponse.SingleChoice.class; }

    @Override
    public List<String> validate(String prompt, QuestionContent.Choice content, AnswerKey.SingleChoice key) {
        List<String> errors = new ArrayList<>();
        Checks.textList(content.options(), 2, 6, "options", errors);
        if (errors.isEmpty() && !Checks.inRange(key.correctIndex(), content.options().size())) {
            errors.add("correctIndex must point to one of the options");
        }
        return errors;
    }

    @Override
    public double grade(QuestionContent.Choice content, AnswerKey.SingleChoice key, LearnerResponse.SingleChoice response) {
        return Objects.equals(response.selected(), key.correctIndex()) ? 1.0 : 0.0;
    }
}

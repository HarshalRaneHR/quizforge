package com.harshal.quizforge.question.handlers;

import com.harshal.quizforge.question.AnswerKey;
import com.harshal.quizforge.question.LearnerResponse;
import com.harshal.quizforge.question.QuestionContent;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class NumericHandler
        implements QuestionTypeHandler<QuestionContent.Numeric, AnswerKey.Numeric, LearnerResponse.Numeric> {

    /** Absorbs floating-point noise like 0.1 + 0.2. */
    private static final double EPSILON = 1e-9;

    @Override public QuestionType type() { return QuestionType.NUMERIC; }
    @Override public Class<QuestionContent.Numeric> contentClass() { return QuestionContent.Numeric.class; }
    @Override public Class<AnswerKey.Numeric> keyClass() { return AnswerKey.Numeric.class; }
    @Override public Class<LearnerResponse.Numeric> responseClass() { return LearnerResponse.Numeric.class; }

    @Override
    public List<String> validate(String prompt, QuestionContent.Numeric content, AnswerKey.Numeric key) {
        List<String> errors = new ArrayList<>();
        if (key.value() == null || key.value().isNaN() || key.value().isInfinite()) errors.add("value must be a number");
        if (key.tolerance() != null && (key.tolerance() < 0 || key.tolerance().isNaN())) errors.add("tolerance must be 0 or more");
        return errors;
    }

    @Override
    public double grade(QuestionContent.Numeric content, AnswerKey.Numeric key, LearnerResponse.Numeric response) {
        if (response.value() == null || response.value().isNaN()) return 0.0;
        double tolerance = key.tolerance() == null ? 0.0 : key.tolerance();
        return Math.abs(response.value() - key.value()) <= tolerance + EPSILON ? 1.0 : 0.0;
    }
}

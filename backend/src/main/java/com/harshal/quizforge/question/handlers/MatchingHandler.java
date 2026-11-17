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

/** Match each left item to a right item. Partial credit per correct pair. */
@Component
public class MatchingHandler
        implements QuestionTypeHandler<QuestionContent.Matching, AnswerKey.Matching, LearnerResponse.Matching> {

    @Override public QuestionType type() { return QuestionType.MATCHING; }
    @Override public Class<QuestionContent.Matching> contentClass() { return QuestionContent.Matching.class; }
    @Override public Class<AnswerKey.Matching> keyClass() { return AnswerKey.Matching.class; }
    @Override public Class<LearnerResponse.Matching> responseClass() { return LearnerResponse.Matching.class; }

    @Override
    public List<String> validate(String prompt, QuestionContent.Matching content, AnswerKey.Matching key) {
        List<String> errors = new ArrayList<>();
        Checks.textList(content.left(), 3, 8, "left items", errors);
        Checks.textList(content.right(), 3, 8, "right items", errors);
        if (!errors.isEmpty()) return errors;
        if (content.left().size() != content.right().size()) {
            errors.add("left and right must have the same number of items");
        } else if (!Checks.isPermutation(key.pairs(), content.left().size())) {
            errors.add("pairs must match every left item to a different right item");
        }
        return errors;
    }

    @Override
    public double grade(QuestionContent.Matching content, AnswerKey.Matching key, LearnerResponse.Matching response) {
        List<Integer> pairs = response.pairs();
        if (pairs == null) return 0.0;
        int right = 0;
        for (int i = 0; i < key.pairs().size() && i < pairs.size(); i++) {
            if (Objects.equals(pairs.get(i), key.pairs().get(i))) right++;
        }
        return (double) right / key.pairs().size();
    }
}

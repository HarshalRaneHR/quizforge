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

/** Put the items in sequence. Partial credit: share of positions that hold the right item. */
@Component
public class OrderingHandler
        implements QuestionTypeHandler<QuestionContent.Ordering, AnswerKey.Ordering, LearnerResponse.Ordering> {

    @Override public QuestionType type() { return QuestionType.ORDERING; }
    @Override public Class<QuestionContent.Ordering> contentClass() { return QuestionContent.Ordering.class; }
    @Override public Class<AnswerKey.Ordering> keyClass() { return AnswerKey.Ordering.class; }
    @Override public Class<LearnerResponse.Ordering> responseClass() { return LearnerResponse.Ordering.class; }

    @Override
    public List<String> validate(String prompt, QuestionContent.Ordering content, AnswerKey.Ordering key) {
        List<String> errors = new ArrayList<>();
        Checks.textList(content.items(), 3, 8, "items", errors);
        if (errors.isEmpty() && !Checks.isPermutation(key.correctOrder(), content.items().size())) {
            errors.add("correctOrder must use every item exactly once");
        }
        return errors;
    }

    @Override
    public double grade(QuestionContent.Ordering content, AnswerKey.Ordering key, LearnerResponse.Ordering response) {
        List<Integer> order = response.order();
        List<Integer> correct = key.correctOrder();
        if (order == null) return 0.0;
        int right = 0;
        for (int k = 0; k < correct.size() && k < order.size(); k++) {
            if (Objects.equals(order.get(k), correct.get(k))) right++;
        }
        return (double) right / correct.size();
    }
}

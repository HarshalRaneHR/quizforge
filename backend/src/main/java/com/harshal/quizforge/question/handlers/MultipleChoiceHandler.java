package com.harshal.quizforge.question.handlers;

import com.harshal.quizforge.question.AnswerKey;
import com.harshal.quizforge.question.LearnerResponse;
import com.harshal.quizforge.question.QuestionContent;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * "Select all that apply". Partial credit: (correct picks − wrong picks) / number of correct options,
 * never below 0 — so ticking every box doesn't earn full marks.
 */
@Component
public class MultipleChoiceHandler
        implements QuestionTypeHandler<QuestionContent.Choice, AnswerKey.MultipleChoice, LearnerResponse.MultipleChoice> {

    @Override public QuestionType type() { return QuestionType.MULTIPLE_CHOICE; }
    @Override public Class<QuestionContent.Choice> contentClass() { return QuestionContent.Choice.class; }
    @Override public Class<AnswerKey.MultipleChoice> keyClass() { return AnswerKey.MultipleChoice.class; }
    @Override public Class<LearnerResponse.MultipleChoice> responseClass() { return LearnerResponse.MultipleChoice.class; }

    @Override
    public List<String> validate(String prompt, QuestionContent.Choice content, AnswerKey.MultipleChoice key) {
        List<String> errors = new ArrayList<>();
        Checks.textList(content.options(), 3, 8, "options", errors);
        if (!errors.isEmpty()) return errors;
        List<Integer> correct = key.correctIndexes();
        int n = content.options().size();
        if (correct == null || correct.isEmpty()) {
            errors.add("at least one option must be correct");
        } else if (correct.stream().anyMatch(i -> !Checks.inRange(i, n)) || new HashSet<>(correct).size() != correct.size()) {
            errors.add("correctIndexes must be distinct indexes of the options");
        } else if (correct.size() == n) {
            errors.add("not every option can be correct");
        }
        return errors;
    }

    @Override
    public double grade(QuestionContent.Choice content, AnswerKey.MultipleChoice key, LearnerResponse.MultipleChoice response) {
        if (response.selected() == null || response.selected().isEmpty()) return 0.0;
        Set<Integer> correct = new HashSet<>(key.correctIndexes());
        Set<Integer> selected = new HashSet<>(response.selected());
        selected.removeIf(Objects::isNull);
        long right = selected.stream().filter(correct::contains).count();
        long wrong = selected.size() - right;
        return Math.max(0.0, (double) (right - wrong) / correct.size());
    }
}

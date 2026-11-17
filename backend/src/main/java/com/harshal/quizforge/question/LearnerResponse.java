package com.harshal.quizforge.question;

import java.util.List;

/**
 * A learner's answer to one question, as sent by the client. Fields are boxed types so a
 * missing value is simply "unanswered" (graded as 0), never a parsing error.
 */
public sealed interface LearnerResponse {

    record SingleChoice(Integer selected) implements LearnerResponse {}

    record MultipleChoice(List<Integer> selected) implements LearnerResponse {}

    record TrueFalse(Boolean value) implements LearnerResponse {}

    record FillBlank(List<String> values) implements LearnerResponse {}

    record Numeric(Double value) implements LearnerResponse {}

    /** order.get(k) = index of the displayed item the learner put at position k. */
    record Ordering(List<Integer> order) implements LearnerResponse {}

    /** pairs.get(i) = right-hand index chosen for left item i (null = not matched). */
    record Matching(List<Integer> pairs) implements LearnerResponse {}
}

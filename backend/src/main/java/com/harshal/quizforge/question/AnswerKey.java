package com.harshal.quizforge.question;

import java.util.List;

/** The correct answer (stored in question.answer_key as JSONB). Only sent to the client after grading. */
public sealed interface AnswerKey {

    record SingleChoice(Integer correctIndex) implements AnswerKey {}

    record MultipleChoice(List<Integer> correctIndexes) implements AnswerKey {}

    record TrueFalse(Boolean answer) implements AnswerKey {}

    /** accepted.get(i) = accepted answers for blank i (compared case- and whitespace-insensitively). */
    record FillBlank(List<List<String>> accepted) implements AnswerKey {}

    record Numeric(Double value, Double tolerance) implements AnswerKey {}

    /** correctOrder.get(k) = index (into the displayed items) of the item that belongs at position k. */
    record Ordering(List<Integer> correctOrder) implements AnswerKey {}

    /** pairs.get(i) = index of the right-hand item that matches left item i. */
    record Matching(List<Integer> pairs) implements AnswerKey {}
}

package com.harshal.quizforge.question;

import java.util.List;

/**
 * What the learner sees for a question (stored in question.content as JSONB).
 * Never contains the answer: ordering items and matching right-hand items are stored shuffled.
 */
public sealed interface QuestionContent {

    /** SINGLE_CHOICE and MULTIPLE_CHOICE. */
    record Choice(List<String> options) implements QuestionContent {}

    record TrueFalse() implements QuestionContent {}

    /** The prompt contains {@code blanks} occurrences of "___". */
    record FillBlank(Integer blanks) implements QuestionContent {}

    /** Optional unit shown next to the input, e.g. "km". */
    record Numeric(String unit) implements QuestionContent {}

    /** Items in a shuffled display order. */
    record Ordering(List<String> items) implements QuestionContent {}

    /** Left items in order; right items shuffled. */
    record Matching(List<String> left, List<String> right) implements QuestionContent {}
}

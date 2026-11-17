package com.harshal.quizforge.question;

import java.util.List;

/**
 * Strategy for one question type: how to validate it and how to grade a response.
 * Adding a new question type = add an enum value, its content/key/response records and one handler;
 * nothing else in the codebase switches on the type.
 *
 * @param <C> content shown to the learner
 * @param <K> answer key
 * @param <R> learner response
 */
public interface QuestionTypeHandler<C extends QuestionContent, K extends AnswerKey, R extends LearnerResponse> {

    QuestionType type();

    Class<C> contentClass();

    Class<K> keyClass();

    Class<R> responseClass();

    /** Problems with this question (empty list = valid). Used for AI output and manual edits. */
    List<String> validate(String prompt, C content, K key);

    /**
     * Fraction of the points earned, from 0.0 to 1.0. {@code response} is never null here,
     * but its fields may be (unanswered parts).
     */
    double grade(C content, K key, R response);
}

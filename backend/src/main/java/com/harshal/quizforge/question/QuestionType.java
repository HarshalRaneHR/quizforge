package com.harshal.quizforge.question;

/** The kinds of questions QuizForge supports. Each has a {@link QuestionTypeHandler}. */
public enum QuestionType {
    /** Pick exactly one option. */
    SINGLE_CHOICE(1),
    /** Pick all options that apply (partial credit, wrong picks are penalised). */
    MULTIPLE_CHOICE(2),
    /** Decide whether a statement is true or false. */
    TRUE_FALSE(1),
    /** Type the missing word(s) for each ___ in the prompt. */
    FILL_BLANK(1),
    /** Enter a number; accepted within a tolerance. */
    NUMERIC(1),
    /** Put items into the correct sequence (partial credit per position). */
    ORDERING(2),
    /** Match each left item to a right item (partial credit per pair). */
    MATCHING(2);

    private final int defaultPoints;

    QuestionType(int defaultPoints) {
        this.defaultPoints = defaultPoints;
    }

    public int defaultPoints() {
        return defaultPoints;
    }
}

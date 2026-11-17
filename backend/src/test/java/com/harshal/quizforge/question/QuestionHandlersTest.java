package com.harshal.quizforge.question;

import com.harshal.quizforge.question.handlers.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Validation and grading rules for every question type. */
class QuestionHandlersTest {

    private static final List<String> FOUR = List.of("a", "b", "c", "d");

    @Nested
    class SingleChoice {
        SingleChoiceHandler h = new SingleChoiceHandler();
        QuestionContent.Choice content = new QuestionContent.Choice(FOUR);
        AnswerKey.SingleChoice key = new AnswerKey.SingleChoice(2);

        @Test void gradesExactMatch() {
            assertThat(h.grade(content, key, new LearnerResponse.SingleChoice(2))).isEqualTo(1.0);
            assertThat(h.grade(content, key, new LearnerResponse.SingleChoice(1))).isZero();
            assertThat(h.grade(content, key, new LearnerResponse.SingleChoice(null))).isZero();
        }

        @Test void rejectsOutOfRangeIndexAndDuplicateOptions() {
            assertThat(h.validate("Q?", content, new AnswerKey.SingleChoice(4))).isNotEmpty();
            assertThat(h.validate("Q?", new QuestionContent.Choice(List.of("a", "A", "c")), key)).isNotEmpty();
            assertThat(h.validate("Q?", content, key)).isEmpty();
        }
    }

    @Nested
    class MultipleChoice {
        MultipleChoiceHandler h = new MultipleChoiceHandler();
        QuestionContent.Choice content = new QuestionContent.Choice(FOUR);
        AnswerKey.MultipleChoice key = new AnswerKey.MultipleChoice(List.of(0, 2));

        @Test void fullPartialAndPenalisedCredit() {
            assertThat(h.grade(content, key, new LearnerResponse.MultipleChoice(List.of(2, 0)))).isEqualTo(1.0);
            assertThat(h.grade(content, key, new LearnerResponse.MultipleChoice(List.of(0)))).isEqualTo(0.5);
            assertThat(h.grade(content, key, new LearnerResponse.MultipleChoice(List.of(0, 1)))).isZero();  // 1 right − 1 wrong
            assertThat(h.grade(content, key, new LearnerResponse.MultipleChoice(List.of(0, 1, 2, 3)))).isZero(); // tick-all earns nothing
            assertThat(h.grade(content, key, new LearnerResponse.MultipleChoice(null))).isZero();
        }

        @Test void allOptionsCorrectIsInvalid() {
            assertThat(h.validate("Q?", content, new AnswerKey.MultipleChoice(List.of(0, 1, 2, 3)))).isNotEmpty();
            assertThat(h.validate("Q?", content, new AnswerKey.MultipleChoice(List.of()))).isNotEmpty();
            assertThat(h.validate("Q?", content, key)).isEmpty();
        }
    }

    @Nested
    class TrueFalse {
        TrueFalseHandler h = new TrueFalseHandler();

        @Test void grades() {
            var key = new AnswerKey.TrueFalse(false);
            assertThat(h.grade(new QuestionContent.TrueFalse(), key, new LearnerResponse.TrueFalse(false))).isEqualTo(1.0);
            assertThat(h.grade(new QuestionContent.TrueFalse(), key, new LearnerResponse.TrueFalse(true))).isZero();
            assertThat(h.validate("S", new QuestionContent.TrueFalse(), new AnswerKey.TrueFalse(null))).isNotEmpty();
        }
    }

    @Nested
    class FillBlank {
        FillBlankHandler h = new FillBlankHandler();
        String prompt = "The ___ is the powerhouse of the ___.";
        QuestionContent.FillBlank content = new QuestionContent.FillBlank(2);
        AnswerKey.FillBlank key = new AnswerKey.FillBlank(List.of(List.of("mitochondrion", "mitochondria"), List.of("cell")));

        @Test void normalisesCaseSpacesAndPunctuation() {
            assertThat(h.grade(content, key, new LearnerResponse.FillBlank(List.of("  Mitochondria ", "CELL.")))).isEqualTo(1.0);
        }

        @Test void partialCreditPerBlank() {
            assertThat(h.grade(content, key, new LearnerResponse.FillBlank(Arrays.asList("mitochondrion", null)))).isEqualTo(0.5);
            assertThat(h.grade(content, key, new LearnerResponse.FillBlank(List.of("nucleus")))).isZero();
        }

        @Test void blankCountMustMatchPrompt() {
            assertThat(h.validate(prompt, content, key)).isEmpty();
            assertThat(h.validate("No blanks here", content, key)).isNotEmpty();
            assertThat(h.validate(prompt, new QuestionContent.FillBlank(1), key)).isNotEmpty();
        }
    }

    @Nested
    class Numeric {
        NumericHandler h = new NumericHandler();
        QuestionContent.Numeric content = new QuestionContent.Numeric("km");

        @Test void acceptsWithinTolerance() {
            var key = new AnswerKey.Numeric(100.0, 0.5);
            assertThat(h.grade(content, key, new LearnerResponse.Numeric(100.5))).isEqualTo(1.0);
            assertThat(h.grade(content, key, new LearnerResponse.Numeric(101.0))).isZero();
        }

        @Test void exactAnswerSurvivesFloatingPointNoise() {
            var key = new AnswerKey.Numeric(0.3, 0.0);
            assertThat(h.grade(content, key, new LearnerResponse.Numeric(0.1 + 0.2))).isEqualTo(1.0);
        }

        @Test void negativeToleranceIsInvalid() {
            assertThat(h.validate("Q?", content, new AnswerKey.Numeric(1.0, -1.0))).isNotEmpty();
        }
    }

    @Nested
    class Ordering {
        OrderingHandler h = new OrderingHandler();
        QuestionContent.Ordering content = new QuestionContent.Ordering(List.of("c", "a", "b"));
        AnswerKey.Ordering key = new AnswerKey.Ordering(List.of(1, 2, 0));   // a, b, c

        @Test void partialCreditPerPosition() {
            assertThat(h.grade(content, key, new LearnerResponse.Ordering(List.of(1, 2, 0)))).isEqualTo(1.0);
            assertThat(h.grade(content, key, new LearnerResponse.Ordering(List.of(1, 0, 2)))).isCloseTo(1.0 / 3, within(1e-9));
        }

        @Test void keyMustBePermutation() {
            assertThat(h.validate("Order", content, new AnswerKey.Ordering(List.of(0, 0, 1)))).isNotEmpty();
            assertThat(h.validate("Order", content, key)).isEmpty();
        }
    }

    @Nested
    class Matching {
        MatchingHandler h = new MatchingHandler();
        QuestionContent.Matching content = new QuestionContent.Matching(List.of("A", "B", "C"), List.of("3", "1", "2"));
        AnswerKey.Matching key = new AnswerKey.Matching(List.of(1, 2, 0));

        @Test void partialCreditPerPair() {
            assertThat(h.grade(content, key, new LearnerResponse.Matching(List.of(1, 2, 0)))).isEqualTo(1.0);
            assertThat(h.grade(content, key, new LearnerResponse.Matching(Arrays.asList(1, null, null)))).isCloseTo(1.0 / 3, within(1e-9));
        }

        @Test void sidesMustHaveEqualSize() {
            var uneven = new QuestionContent.Matching(List.of("A", "B", "C"), List.of("1", "2", "3", "4"));
            assertThat(h.validate("Match", uneven, key)).isNotEmpty();
            assertThat(h.validate("Match", content, key)).isEmpty();
        }
    }
}

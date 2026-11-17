package com.harshal.quizforge.question;

import com.harshal.quizforge.question.handlers.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionTypeRegistryTest {

    private final QuestionTypeRegistry registry = new QuestionTypeRegistry(List.of(
            new SingleChoiceHandler(), new MultipleChoiceHandler(), new TrueFalseHandler(), new FillBlankHandler(),
            new NumericHandler(), new OrderingHandler(), new MatchingHandler()));

    private final QuestionFactory factory = new QuestionFactory(new Random(1));

    @Test
    void failsFastWhenAHandlerIsMissing() {
        assertThatThrownBy(() -> new QuestionTypeRegistry(List.of(new SingleChoiceHandler())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void gradesFromStoredJsonAndRawClientResponse() {
        QuestionDraft d = factory.multipleChoice(Difficulty.EASY, "Pick primes", List.of("2", "3", "4", "9"), List.of(0, 1), "x");
        String content = registry.toJson(d.content());
        String key = registry.toJson(d.answerKey());
        List<Integer> correct = ((AnswerKey.MultipleChoice) d.answerKey()).correctIndexes();

        assertThat(registry.grade(QuestionType.MULTIPLE_CHOICE, content, key, Map.of("selected", correct))).isEqualTo(1.0);
        assertThat(registry.grade(QuestionType.MULTIPLE_CHOICE, content, key, Map.of("selected", List.of(correct.get(0))))).isEqualTo(0.5);
    }

    @Test
    void missingOrMalformedResponsesScoreZeroInsteadOfFailing() {
        QuestionDraft d = factory.trueFalse(Difficulty.EASY, "Water boils at 100C at sea level", true, "x");
        String content = registry.toJson(d.content());
        String key = registry.toJson(d.answerKey());

        assertThat(registry.grade(QuestionType.TRUE_FALSE, content, key, null)).isZero();
        assertThat(registry.grade(QuestionType.TRUE_FALSE, content, key, Map.of())).isZero();
        assertThat(registry.grade(QuestionType.TRUE_FALSE, content, key, Map.of("value", List.of("nonsense")))).isZero();
        assertThat(registry.grade(QuestionType.TRUE_FALSE, content, key, Map.of("value", true))).isEqualTo(1.0);
    }

    @Test
    void validateCatchesMismatchedContentAndBadPoints() {
        QuestionDraft good = factory.trueFalse(Difficulty.EASY, "S", true, "x");
        assertThat(registry.validate(good)).isEmpty();

        var wrongType = new QuestionDraft(QuestionType.ORDERING, Difficulty.EASY, 1, "S", good.content(), good.answerKey(), "x");
        assertThat(registry.validate(wrongType)).isNotEmpty();

        var badPoints = new QuestionDraft(QuestionType.TRUE_FALSE, Difficulty.EASY, 0, "S", good.content(), good.answerKey(), "x");
        assertThat(registry.validate(badPoints)).anyMatch(e -> e.contains("points"));
    }

    @Test
    void contentJsonNeverContainsTheAnswer() {
        QuestionDraft d = factory.singleChoice(Difficulty.EASY, "Q", List.of("a", "b", "c", "d"), 1, "x");
        assertThat(registry.toJson(d.content())).doesNotContain("correct");
    }
}

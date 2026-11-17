package com.harshal.quizforge.question;

import com.harshal.quizforge.question.QuestionFactory.Pair;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** The factory shuffles what learners see, but the answer key must still point at the right items. */
class QuestionFactoryTest {

    private final QuestionFactory factory = new QuestionFactory(new Random());

    @RepeatedTest(20)
    void singleChoiceKeyFollowsTheShuffledCorrectOption() {
        QuestionDraft d = factory.singleChoice(Difficulty.EASY, "Capital of France?", List.of("Paris", "Rome", "Oslo", "Lima"), 0, "why");
        var options = ((QuestionContent.Choice) d.content()).options();
        int key = ((AnswerKey.SingleChoice) d.answerKey()).correctIndex();
        assertThat(options.get(key)).isEqualTo("Paris");
    }

    @RepeatedTest(20)
    void orderingIsShuffledAndKeyRebuildsCorrectSequence() {
        List<String> correct = List.of("Plan", "Build", "Test", "Ship");
        QuestionDraft d = factory.ordering(Difficulty.MEDIUM, "Order the steps", correct, "why");
        var shown = ((QuestionContent.Ordering) d.content()).items();
        var key = ((AnswerKey.Ordering) d.answerKey()).correctOrder();

        assertThat(shown).isNotEqualTo(correct);                       // never shown already solved
        assertThat(key.stream().map(shown::get).toList()).isEqualTo(correct);
    }

    @RepeatedTest(20)
    void matchingKeyPairsEachLeftWithItsOriginalRight() {
        var pairs = List.of(new Pair("H2O", "Water"), new Pair("NaCl", "Salt"), new Pair("CO2", "Carbon dioxide"));
        QuestionDraft d = factory.matching(Difficulty.MEDIUM, "Match formulas", pairs, "why");
        var content = (QuestionContent.Matching) d.content();
        var key = ((AnswerKey.Matching) d.answerKey()).pairs();

        for (int i = 0; i < pairs.size(); i++) {
            assertThat(content.right().get(key.get(i))).isEqualTo(pairs.get(i).right());
        }
    }

    @Test
    void usesTypeDefaultPoints() {
        assertThat(factory.trueFalse(Difficulty.EASY, "Sky is blue", true, "x").points()).isEqualTo(1);
        assertThat(factory.ordering(Difficulty.EASY, "o", List.of("a", "b", "c"), "x").points()).isEqualTo(2);
    }
}

package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.question.QuestionTypeRegistry;
import com.harshal.quizforge.quiz.StudioDtos.AuthoringQuestion;
import com.harshal.quizforge.quiz.StudioDtos.MatchPair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Editor form → stored question → editor form must give back exactly what the author entered. */
class AuthoringCodecTest {

    private final QuestionTypeRegistry registry = QuizServiceTest.registry();
    private final AuthoringCodec codec = new AuthoringCodec(registry);
    private final QuestionMapper mapper = new QuestionMapper(registry);

    private static AuthoringQuestion q(QuestionType type, String prompt) {
        return new AuthoringQuestion(type, Difficulty.HARD, 3, prompt, "because", null, null, null, null, null, null, null, null, null);
    }

    static Stream<AuthoringQuestion> everyType() {
        return Stream.of(
                new AuthoringQuestion(QuestionType.SINGLE_CHOICE, Difficulty.EASY, 1, "Capital?", "x",
                        List.of("Rome", "Paris", "Oslo"), List.of(1), null, null, null, null, null, null, null),
                new AuthoringQuestion(QuestionType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 2, "Primes?", "x",
                        List.of("2", "4", "5", "9"), List.of(0, 2), null, null, null, null, null, null, null),
                new AuthoringQuestion(QuestionType.TRUE_FALSE, Difficulty.EASY, 1, "Water is wet", "x",
                        null, null, false, null, null, null, null, null, null),
                new AuthoringQuestion(QuestionType.FILL_BLANK, Difficulty.MEDIUM, 2, "The ___ of the ___", "x",
                        null, null, null, List.of(List.of("heart"), List.of("matter", "issue")), null, null, null, null, null),
                new AuthoringQuestion(QuestionType.NUMERIC, Difficulty.HARD, 1, "Boiling point?", "x",
                        null, null, null, null, 100.0, 0.5, "C", null, null),
                new AuthoringQuestion(QuestionType.ORDERING, Difficulty.MEDIUM, 2, "Order the steps", "x",
                        null, null, null, null, null, null, null, List.of("Plan", "Build", "Test", "Ship"), null),
                new AuthoringQuestion(QuestionType.MATCHING, Difficulty.MEDIUM, 3, "Match", "x",
                        null, null, null, null, null, null, null, null,
                        List.of(new MatchPair("H2O", "Water"), new MatchPair("NaCl", "Salt"), new MatchPair("CO2", "Carbon dioxide"))));
    }

    @ParameterizedTest
    @MethodSource("everyType")
    void roundTripsEveryQuestionType(AuthoringQuestion input) {
        Question stored = mapper.toEntity(codec.toDraft(input));
        assertThat(codec.toAuthoring(stored)).isEqualTo(input);
    }

    @Test
    void authorsKeepTheirOptionOrder() {
        var input = new AuthoringQuestion(QuestionType.SINGLE_CHOICE, null, null, "Q", null,
                List.of("first", "second", "third"), List.of(2), null, null, null, null, null, null, null);
        assertThat(mapper.toEntity(codec.toDraft(input)).getContentJson()).contains("[\"first\",\"second\",\"third\"]");
    }

    @Test
    void invalidQuestionsListEveryProblem() {
        var tooFewOptions = new AuthoringQuestion(QuestionType.SINGLE_CHOICE, null, null, "Q", null,
                List.of("only"), List.of(0), null, null, null, null, null, null, null);
        assertThatThrownBy(() -> codec.toDraft(tooFewOptions)).isInstanceOf(BadRequestException.class).hasMessageContaining("options");

        assertThatThrownBy(() -> codec.toDraft(q(QuestionType.ORDERING, "Order"))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> codec.toDraft(q(QuestionType.TRUE_FALSE, "S"))).isInstanceOf(BadRequestException.class);

        var twoCorrectInSingle = new AuthoringQuestion(QuestionType.SINGLE_CHOICE, null, null, "Q", null,
                List.of("a", "b"), List.of(0, 1), null, null, null, null, null, null, null);
        assertThatThrownBy(() -> codec.toDraft(twoCorrectInSingle)).hasMessageContaining("exactly one");
    }
}

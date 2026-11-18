package com.harshal.quizforge.library;

import com.harshal.quizforge.library.BuiltInQuizSeeder.QuizDefinition;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.quiz.AuthoringCodec;
import com.harshal.quizforge.quiz.QuizServiceTest;
import com.harshal.quizforge.quiz.StudioDtos.AuthoringQuestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Every built-in question must pass the same validation as the Studio editor, so bad content fails the build. */
class BuiltInQuizzesTest {

    private final AuthoringCodec codec = new AuthoringCodec(QuizServiceTest.registry());

    static List<QuizDefinition> quizzes() {
        return BuiltInQuizSeeder.load();
    }

    @Test
    void theLibraryHasFiveQuizzesWithUniqueSlugs() {
        List<QuizDefinition> all = quizzes();
        assertThat(all).extracting(QuizDefinition::slug)
                .containsExactlyInAnyOrder("el-nino", "java-fundamentals", "llm-essentials", "python-basics", "sql-databases")
                .doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("quizzes")
    void eachQuizHasTwentyValidQuestionsCoveringEveryType(QuizDefinition quiz) {
        assertThat(quiz.title()).isNotBlank();
        assertThat(quiz.questions()).hasSize(20);
        assertThat(quiz.questions().stream().map(AuthoringQuestion::type).collect(Collectors.toSet()))
                .isEqualTo(EnumSet.allOf(QuestionType.class));
        for (int i = 0; i < quiz.questions().size(); i++) {
            AuthoringQuestion q = quiz.questions().get(i);
            assertThatCode(() -> codec.toDraft(q))
                    .as("%s question %d: %s", quiz.slug(), i + 1, q.prompt())
                    .doesNotThrowAnyException();
            assertThat(q.explanation()).as("%s question %d needs an explanation", quiz.slug(), i + 1).isNotBlank();
        }
    }
}

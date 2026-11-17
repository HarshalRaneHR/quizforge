package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.quiz.QuizDtos.QuestionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringTest {

    private static Question question(long id, int correctIndex) {
        Question q = new Question();
        q.setId(id);
        q.setCorrectIndex(correctIndex);
        q.setExplanation("because");
        return q;
    }

    @Test
    void gradesEachAnswer() {
        List<QuestionResult> results = Scoring.grade(
                List.of(question(1, 0), question(2, 3)),
                List.of(0, 1));

        assertThat(results).extracting(QuestionResult::isCorrect).containsExactly(true, false);
        assertThat(results.get(1).correct()).isEqualTo(3);
    }

    @Test
    void rejectsWrongNumberOfAnswers() {
        assertThatThrownBy(() -> Scoring.grade(List.of(question(1, 0)), List.of(0, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Expected 1 answers");
    }
}

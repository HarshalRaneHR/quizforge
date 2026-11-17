package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.quiz.QuizDtos.QuestionResult;

import java.util.ArrayList;
import java.util.List;

/** Pure grading logic — no Spring, easy to unit test. */
final class Scoring {
    private Scoring() {}

    static List<QuestionResult> grade(List<Question> questions, List<Integer> answers) {
        if (answers.size() != questions.size()) {
            throw new BadRequestException("Expected " + questions.size() + " answers, got " + answers.size());
        }
        List<QuestionResult> results = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            int chosen = answers.get(i);
            results.add(new QuestionResult(q.getId(), chosen, q.getCorrectIndex(),
                    chosen == q.getCorrectIndex(), q.getExplanation()));
        }
        return results;
    }
}

package com.harshal.quizforge.question;

/** A complete question before it's saved — produced by the AI generator (and later the quiz builder). */
public record QuestionDraft(
        QuestionType type,
        Difficulty difficulty,
        int points,
        String prompt,
        QuestionContent content,
        AnswerKey answerKey,
        String explanation) {
}

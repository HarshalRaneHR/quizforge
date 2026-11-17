package com.harshal.quizforge.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How a quiz is taken. Stored as columns of the quiz table (JPA @Embeddable). Null = no limit. */
@Embeddable
@Getter @Setter @NoArgsConstructor
public class QuizSettings {

    /** FULL: score, correct answers and explanations. SCORE_ONLY: just the score (exam mode). */
    public enum ResultsVisibility { FULL, SCORE_ONLY }

    private Integer maxAttempts;

    private Integer passPercentage;

    @Column(nullable = false)
    private boolean shuffleQuestions;

    /** Shuffles choice options per attempt (ordering/matching items are always stored shuffled). */
    @Column(nullable = false)
    private boolean shuffleOptions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultsVisibility resultsVisibility = ResultsVisibility.FULL;

    public QuizSettings copy() {
        QuizSettings s = new QuizSettings();
        s.maxAttempts = maxAttempts;
        s.passPercentage = passPercentage;
        s.shuffleQuestions = shuffleQuestions;
        s.shuffleOptions = shuffleOptions;
        s.resultsVisibility = resultsVisibility;
        return s;
    }
}

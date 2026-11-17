package com.harshal.quizforge.quiz;

import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

/**
 * One question of any type. Type-specific data lives in two JSONB columns, read and written as JSON
 * text and interpreted by QuestionTypeRegistry — one table serves all question types.
 */
@Entity
@Getter @Setter @NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;

    /** 0-based order within the quiz. */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    /** What the learner sees (options, items…). JSON text; cast to jsonb on write. */
    @Column(name = "content", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String contentJson;

    /** The correct answer. Never sent to the client before grading. */
    @Column(name = "answer_key", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String answerKeyJson;

    @Column(columnDefinition = "TEXT")
    private String explanation;
}

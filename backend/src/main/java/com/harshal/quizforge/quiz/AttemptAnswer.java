package com.harshal.quizforge.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

/** One graded answer inside an attempt — kept for reviews and per-question analytics. */
@Entity
@Getter @Setter @NoArgsConstructor
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Question question;

    /** The learner's raw response as JSON (null = unanswered). */
    @Column(name = "response", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String responseJson;

    private double pointsAwarded;

    private double maxPoints;

    private boolean correct;

    /** "Flag for review" while taking the quiz. */
    private boolean flagged;

    private Instant updatedAt;
}

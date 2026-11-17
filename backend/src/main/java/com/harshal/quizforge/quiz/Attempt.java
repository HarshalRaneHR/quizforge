package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One learner's go at a quiz: opened by "Start", answers autosaved (resumable), then submitted. */
@Entity
@Getter @Setter @NoArgsConstructor
public class Attempt {

    public enum Status { IN_PROGRESS, SUBMITTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.IN_PROGRESS;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant submittedAt;

    /** Seeds this attempt's question/option shuffle, so a resumed attempt looks the same. */
    private long shuffleSeed;

    /** Points earned (fractional with partial credit). */
    private double score;

    private double maxScore;

    /** Null when the quiz has no pass mark. */
    private Boolean passed;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttemptAnswer> answers = new ArrayList<>();

    public void addAnswer(AttemptAnswer answer) {
        answer.setAttempt(this);
        answers.add(answer);
    }

    public Optional<AttemptAnswer> answerFor(Long questionId) {
        return answers.stream().filter(a -> a.getQuestion().getId().equals(questionId)).findFirst();
    }

    public boolean isOpen() {
        return status == Status.IN_PROGRESS;
    }
}

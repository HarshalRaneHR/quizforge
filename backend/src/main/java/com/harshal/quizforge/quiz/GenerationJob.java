package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

/** A background AI generation run. The client polls it for progress instead of holding an HTTP request open. */
@Entity
@Getter @Setter @NoArgsConstructor
public class GenerationJob {

    public enum Target { NEW_PRACTICE_QUIZ, APPEND_TO_QUIZ }

    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    /** Random UUID: job ids can't be guessed or enumerated. */
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    /** The created quiz (NEW_PRACTICE_QUIZ, set on success) or the draft being extended (APPEND_TO_QUIZ). */
    private Long quizId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Target target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** The original request (title, source text, blueprint) as JSON. */
    @Column(name = "request", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String requestJson;

    private int totalSteps;

    private int doneSteps;

    private int questionCount;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant finishedAt;

    public boolean isFinished() {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }
}

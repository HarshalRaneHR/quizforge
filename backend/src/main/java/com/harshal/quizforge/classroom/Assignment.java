package com.harshal.quizforge.classroom;

import com.harshal.quizforge.quiz.Quiz;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A published quiz assigned to a classroom, optionally with a due date. */
@Entity
@Getter @Setter @NoArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;

    /** Null = no due date. Learners can still submit late; it's shown as overdue. */
    private Instant dueAt;

    @Column(nullable = false)
    private Instant createdAt;
}

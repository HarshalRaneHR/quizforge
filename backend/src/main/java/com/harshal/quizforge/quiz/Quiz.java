package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter @NoArgsConstructor
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Study material for AI-generated practice quizzes (null for hand-built ones). */
    @Column(columnDefinition = "TEXT")
    private String sourceText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizStatus status = QuizStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PRIVATE;

    @Embedded
    private QuizSettings settings = new QuizSettings();

    // Stored so list pages don't load every question (avoids N+1).
    private int questionCount;

    private int totalPoints;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User owner;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Question> questions = new ArrayList<>();

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    private Instant publishedAt;

    /** Appends a question, keeping position, questionCount and totalPoints in sync. */
    public void addQuestion(Question question) {
        question.setQuiz(this);
        questions.add(question);
        recalculate();
    }

    public void removeQuestion(Question question) {
        questions.remove(question);   // orphanRemoval deletes the row
        recalculate();
    }

    /** Re-derives positions and the denormalised totals after any change to the question list. */
    public void recalculate() {
        for (int i = 0; i < questions.size(); i++) questions.get(i).setPosition(i);
        questionCount = questions.size();
        totalPoints = questions.stream().mapToInt(Question::getPoints).sum();
        updatedAt = Instant.now();
    }

    public boolean isDraft() {
        return status == QuizStatus.DRAFT;
    }

    /** Anyone signed in can find and take it. */
    public boolean isListed() {
        return status == QuizStatus.PUBLISHED && visibility == Visibility.PUBLIC;
    }
}

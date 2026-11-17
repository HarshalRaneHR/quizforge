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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    // Stored so the list page doesn't load every question (avoids N+1).
    private int questionCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User owner;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Question> questions = new ArrayList<>();

    private Instant createdAt = Instant.now();

    public void addQuestion(Question question) {
        question.setQuiz(this);
        questions.add(question);
    }
}

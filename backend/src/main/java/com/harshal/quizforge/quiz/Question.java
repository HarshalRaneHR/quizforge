package com.harshal.quizforge.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter @NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    @BatchSize(size = 50)       // loads options for 50 questions in one query instead of one each
    private List<String> options = new ArrayList<>();

    private int correctIndex;

    @Column(columnDefinition = "TEXT")
    private String explanation;
}

package com.harshal.quizforge.classroom;

import com.harshal.quizforge.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A class run by an instructor. Learners join with a short code; quizzes are assigned to everyone in it. */
@Entity
@Getter @Setter @NoArgsConstructor
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true, length = 12)
    private String joinCode;

    @Column(nullable = false)
    private Instant createdAt;

    // @BatchSize: listing N classrooms loads their members/assignments in a few queries, not N each.
    @OneToMany(mappedBy = "classroom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("joinedAt")
    @BatchSize(size = 50)
    private List<ClassroomMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "classroom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt")
    @BatchSize(size = 50)
    private List<Assignment> assignments = new ArrayList<>();
}

package com.harshal.quizforge.demo;

import com.harshal.quizforge.auth.Role;
import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.classroom.Assignment;
import com.harshal.quizforge.classroom.Classroom;
import com.harshal.quizforge.classroom.ClassroomMember;
import com.harshal.quizforge.classroom.ClassroomRepository;
import com.harshal.quizforge.question.Difficulty;
import com.harshal.quizforge.question.QuestionType;
import com.harshal.quizforge.quiz.AuthoringCodec;
import com.harshal.quizforge.quiz.QuestionMapper;
import com.harshal.quizforge.quiz.Quiz;
import com.harshal.quizforge.quiz.QuizRepository;
import com.harshal.quizforge.quiz.QuizStatus;
import com.harshal.quizforge.quiz.StudioDtos.AuthoringQuestion;
import com.harshal.quizforge.quiz.StudioDtos.MatchPair;
import com.harshal.quizforge.quiz.Visibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * DEMO_DATA=true: creates a demo instructor and learner, a published quiz with all seven question
 * types, and a classroom (code QFDEMO26) the learner has joined, so the app can be tried in seconds.
 * Runs once (skipped if the demo instructor exists). Never enable it on a real deployment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    static final String INSTRUCTOR = "demo-instructor@quizforge.dev";
    static final String LEARNER = "demo-learner@quizforge.dev";
    static final String JOIN_CODE = "QFDEMO26";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final QuizRepository quizzes;
    private final ClassroomRepository classrooms;
    private final AuthoringCodec codec;
    private final QuestionMapper mapper;
    private final Clock clock;

    @Value("${app.demo.password}")
    private String password;

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByEmail(INSTRUCTOR)) return;
        Instant now = clock.instant();
        User instructor = user(INSTRUCTOR, "Demo Instructor", Role.INSTRUCTOR);
        User learner = user(LEARNER, "Demo Learner", Role.LEARNER);

        Quiz quiz = new Quiz();
        quiz.setTitle("Demo: How the Internet Works");
        quiz.setDescription("A tour of every question type QuizForge supports.");
        quiz.setOwner(instructor);
        quiz.setVisibility(Visibility.PUBLIC);
        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setPublishedAt(now);
        quiz.getSettings().setPassPercentage(60);
        questions().forEach(q -> quiz.addQuestion(mapper.toEntity(codec.toDraft(q))));
        quizzes.save(quiz);

        Classroom classroom = new Classroom();
        classroom.setOwner(instructor);
        classroom.setName("Demo class");
        classroom.setDescription("Join with code " + JOIN_CODE);
        classroom.setJoinCode(JOIN_CODE);
        classroom.setCreatedAt(now);
        ClassroomMember member = new ClassroomMember();
        member.setClassroom(classroom);
        member.setUser(learner);
        member.setJoinedAt(now);
        classroom.getMembers().add(member);
        Assignment assignment = new Assignment();
        assignment.setClassroom(classroom);
        assignment.setQuiz(quiz);
        assignment.setDueAt(now.plus(Duration.ofDays(7)));
        assignment.setCreatedAt(now);
        classroom.getAssignments().add(assignment);
        classrooms.save(classroom);

        log.info("Demo data created: {} / {} (password from DEMO_PASSWORD), classroom code {}", INSTRUCTOR, LEARNER, JOIN_CODE);
    }

    /** Demo accounts use the published demo password, so no forced password change. */
    private User user(String email, String fullName, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(fullName);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        return users.save(u);
    }

    private static List<AuthoringQuestion> questions() {
        return List.of(
                new AuthoringQuestion(QuestionType.SINGLE_CHOICE, Difficulty.EASY, 1, "What does DNS translate a domain name into?",
                        "DNS maps names like example.com to IP addresses.",
                        List.of("An IP address", "A MAC address", "A port number", "A URL path"), List.of(0),
                        null, null, null, null, null, null, null),
                new AuthoringQuestion(QuestionType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 2, "Which of these are transport-layer protocols?",
                        "TCP and UDP are transport protocols; HTTP and DNS are application protocols.",
                        List.of("TCP", "HTTP", "UDP", "DNS"), List.of(0, 2), null, null, null, null, null, null, null),
                new AuthoringQuestion(QuestionType.TRUE_FALSE, Difficulty.EASY, 1, "UDP guarantees that packets arrive in order.",
                        "UDP gives no delivery or ordering guarantees; TCP does.", null, null, false, null, null, null, null, null, null),
                new AuthoringQuestion(QuestionType.FILL_BLANK, Difficulty.MEDIUM, 1, "The default port for HTTPS is ___.",
                        "HTTPS uses port 443 (HTTP uses 80).", null, null, null, List.of(List.of("443")), null, null, null, null, null),
                new AuthoringQuestion(QuestionType.NUMERIC, Difficulty.MEDIUM, 1, "How many bits long is an IPv4 address?",
                        "IPv4 addresses are 32 bits, written as four 8-bit numbers.", null, null, null, null, 32.0, 0.0, "bits", null, null),
                new AuthoringQuestion(QuestionType.ORDERING, Difficulty.MEDIUM, 2, "Put the steps of loading a website in order.",
                        "Resolve the name, connect, secure the connection, then request and receive the page.",
                        null, null, null, null, null, null, null,
                        List.of("DNS lookup", "TCP handshake", "TLS handshake", "HTTP request", "HTTP response"), null),
                new AuthoringQuestion(QuestionType.MATCHING, Difficulty.EASY, 2, "Match each HTTP status code to its meaning.",
                        "2xx success, 4xx client error, 5xx server error.",
                        null, null, null, null, null, null, null, null,
                        List.of(new MatchPair("200", "OK"), new MatchPair("404", "Not Found"), new MatchPair("500", "Internal Server Error"))));
    }
}

package com.harshal.quizforge.library;

import com.harshal.quizforge.auth.Role;
import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.quiz.AuthoringCodec;
import com.harshal.quizforge.quiz.QuestionMapper;
import com.harshal.quizforge.quiz.Quiz;
import com.harshal.quizforge.quiz.QuizRepository;
import com.harshal.quizforge.quiz.QuizStatus;
import com.harshal.quizforge.quiz.StudioDtos.AuthoringQuestion;
import com.harshal.quizforge.quiz.Visibility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Seeds the built-in quiz library from {@code classpath:seed/quizzes/*.json} on startup.
 * Library quizzes are published for everyone, read-only, and can never be deleted (API rule + DB trigger).
 * Idempotent: a quiz whose slug already exists is left untouched.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.library.enabled", havingValue = "true", matchIfMissing = true)
public class BuiltInQuizSeeder implements CommandLineRunner {

    static final String LIBRARY_ACCOUNT = "library@quizforge.app";
    static final String LOCATION = "classpath:seed/quizzes/*.json";

    /** One JSON file = one quiz. Questions use the Studio editor's natural form. */
    public record QuizDefinition(String slug, String title, String description, Integer passPercentage,
                                 List<AuthoringQuestion> questions) {}

    private final UserRepository users;
    private final QuizRepository quizzes;
    private final AuthoringCodec codec;
    private final QuestionMapper mapper;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public BuiltInQuizSeeder(UserRepository users, QuizRepository quizzes, AuthoringCodec codec, QuestionMapper mapper,
                             PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.quizzes = quizzes;
        this.codec = codec;
        this.mapper = mapper;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        for (QuizDefinition def : load()) {
            if (quizzes.existsByBuiltInSlug(def.slug())) continue;
            quizzes.save(toQuiz(def, libraryAccount()));
            created++;
        }
        if (created > 0) log.info("Built-in library: created {} quiz(zes)", created);
    }

    /** Reads every definition (sorted by file name for a stable order). */
    public static List<QuizDefinition> load() {
        JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources(LOCATION);
            List<QuizDefinition> defs = new ArrayList<>();
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    defs.add(json.readValue(in, QuizDefinition.class));
                }
            }
            defs.sort(Comparator.comparing(QuizDefinition::slug));
            return defs;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read built-in quizzes", e);
        }
    }

    /** Validates every question with the same rules as the Studio editor (fails startup on bad content). */
    Quiz toQuiz(QuizDefinition def, User owner) {
        Quiz quiz = new Quiz();
        quiz.setBuiltInSlug(def.slug());
        quiz.setTitle(def.title());
        quiz.setDescription(def.description());
        quiz.setOwner(owner);
        quiz.setVisibility(Visibility.PUBLIC);
        quiz.setStatus(QuizStatus.PUBLISHED);
        quiz.setPublishedAt(clock.instant());
        quiz.getSettings().setPassPercentage(def.passPercentage());
        quiz.getSettings().setShuffleOptions(true);
        def.questions().forEach(q -> quiz.addQuestion(mapper.toEntity(codec.toDraft(q))));
        return quiz;
    }

    /** System account that owns the library. Its password is random and never stored anywhere: nobody can log in as it. */
    private User libraryAccount() {
        return users.findByEmail(LIBRARY_ACCOUNT).orElseGet(() -> {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            User u = new User();
            u.setEmail(LIBRARY_ACCOUNT);
            u.setFullName("QuizForge Library");
            u.setPasswordHash(encoder.encode(Base64.getEncoder().encodeToString(secret)));
            u.setRole(Role.INSTRUCTOR);
            // System account that only owns the library quizzes: nobody ever logs in as it.
            u.setStatus(com.harshal.quizforge.auth.AccountStatus.DEACTIVATED);
            return users.save(u);
        });
    }
}

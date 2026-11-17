# QuizForge — Build Guide

**AI Quiz & Learning Tracker** · React + TypeScript · Spring Boot 3 · PostgreSQL · Spring AI · Docker

Paste study material → get an AI-generated multiple-choice quiz → take it → see your score with explanations → track your history.

This guide takes you from an empty folder to a deployed-on-your-laptop, GitHub-ready project. Every phase ends with a way to **prove it works** before you move on.

**Time estimate:** 2–3 weekends (≈ 20–25 hours).

---

## 0. Why this project, and what it proves

Your resume says you built this. Every claim must map to real code you can open in an interview:

| Resume claim | Where it lives in this project |
|---|---|
| React + TypeScript frontend | `frontend/src/**` |
| TanStack Query, React Router | `Dashboard.tsx`, `TakeQuiz.tsx`, `App.tsx` |
| Spring Boot 3 REST API | `backend/.../QuizController.java`, `AuthController.java` |
| Spring Data JPA / Hibernate on PostgreSQL | entities + repositories (Phase 2) |
| Spring Security + JWT, role-based access | `security/*`, `/api/admin/**` is ADMIN-only (Phase 4) |
| DTO validation | `@Valid` + Jakarta annotations on request records |
| Global exception handling | `GlobalExceptionHandler` (Phase 3) |
| Pagination | `GET /api/quizzes?page=0&size=10`, `GET /api/attempts/me` |
| Spring AI (LLM calls) | `AiQuizGenerator.java` (Phase 5) |
| JUnit / Mockito tests | `ScoringTest`, `QuizServiceTest` (Phase 7) |
| Docker Compose | `docker-compose.yml` + two Dockerfiles (Phase 9) |

> **Rule:** don't send the resume version with QuizForge until Phases 1–9 are done and pushed to GitHub.

---

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java (Temurin) | 21 | `java -version` |
| Node.js | 20+ | `node -v` |
| Docker Desktop | latest | `docker compose version` |
| IDE | IntelliJ IDEA Community (backend), VS Code (frontend) | — |
| LLM key | OpenAI API key (≈ $1 is plenty) **or** Ollama (free, local) | — |
| `jq` (optional, pretty JSON in terminal) | any | `brew install jq` |

Install on macOS:

```bash
brew install --cask temurin@21 docker
brew install node jq
```

Generate a JWT secret now and keep it for later:

```bash
openssl rand -base64 32
```

---

## 2. Architecture

```
┌──────────────────────────┐       /api/*        ┌─────────────────────────────┐
│ React + TS (Vite)        │ ──────────────────▶ │ Spring Boot 3 (port 8080)   │
│ TanStack Query, Router   │   Bearer <JWT>      │ Security (JWT filter)       │
│ nginx in Docker (:3000)  │ ◀────────────────── │ Controllers → Services      │
└──────────────────────────┘        JSON         │ Spring Data JPA ─┐          │
                                                 │ Spring AI ───────┼──▶ OpenAI│
                                                 └──────────────────┼──────────┘
                                                                    ▼
                                                           PostgreSQL 16
```

### API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create account, returns JWT |
| POST | `/api/auth/login` | public | Login, returns JWT |
| POST | `/api/quizzes/generate` | user | AI-generate and save a quiz |
| GET | `/api/quizzes?page=&size=` | user | My quizzes (paginated) |
| GET | `/api/quizzes/{id}` | owner | Quiz to take (**no answers included**) |
| DELETE | `/api/quizzes/{id}` | owner or ADMIN | Delete quiz + its attempts |
| POST | `/api/quizzes/{id}/attempts` | owner | Submit answers, get score + explanations |
| GET | `/api/attempts/me?page=&size=` | user | My attempt history |
| GET | `/api/admin/users?page=&size=` | ADMIN | List all users |

### Data model

```
users (id, email UNIQUE, password_hash, role, created_at)
  │1
  │*
quiz (id, title, source_text, question_count, owner_id → users, created_at)
  │1                                  │1
  │*                                  │*
question (id, quiz_id, prompt,     attempt (id, user_id → users, quiz_id,
          correct_index,                     score, total, created_at)
          explanation)
  │1
  │*
question_options (question_id, option_index, option_text)
```

---

## 3. Repository layout

```
quizforge/
├── backend/                 Spring Boot app
├── frontend/                Vite React app
├── docker-compose.yml
├── .env.example             committed (no secrets)
├── .env                     NOT committed
├── .gitignore
└── README.md
```

```bash
mkdir -p ~/Desktop/Learn/Java/quizforge && cd ~/Desktop/Learn/Java/quizforge
git init
```

`.gitignore`:

```gitignore
.env
backend/target/
frontend/node_modules/
frontend/dist/
.idea/
.DS_Store
```

`.env.example` (copy to `.env` and fill in):

```bash
OPENAI_API_KEY=sk-...
JWT_SECRET=paste-the-openssl-output-here
ADMIN_EMAIL=admin@quizforge.dev
ADMIN_PASSWORD=change-me-please
```

---

## Phase 1 — Backend skeleton (≈ 1 h)

### 1.1 Generate the project

Go to **https://start.spring.io**:

- Project: **Maven** · Language: **Java** · Spring Boot: latest **3.x** stable
- Group: `com.harshal` · Artifact: `quizforge` · Package: `com.harshal.quizforge`
- Java: **21** · Packaging: Jar
- Dependencies:
  - Spring Web
  - Spring Data JPA
  - PostgreSQL Driver
  - Spring Security
  - Validation
  - Lombok
  - **OpenAI** (Spring AI) — or **Ollama** if you want free/local

Download, unzip into `quizforge/backend`.

> Initializr adds the Spring AI BOM to `pom.xml` for you — don't hand-pick Spring AI versions.

### 1.2 Add JWT library

In `backend/pom.xml`, inside `<dependencies>`:

```xml
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
```

### 1.3 Configuration

Delete `src/main/resources/application.properties` and create `application.yml`:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/quizforge}
    username: ${DB_USER:quizforge}
    password: ${DB_PASSWORD:quizforge}
  jpa:
    hibernate:
      ddl-auto: update        # fine for a portfolio project; see Stretch goals for Flyway
    open-in-view: false       # forces you to load data inside @Transactional services (good practice)
  data:
    web:
      pageable:
        max-page-size: 50
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.3

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-minutes: 120
  admin:
    email: ${ADMIN_EMAIL:}
    password: ${ADMIN_PASSWORD:}
```

**Using Ollama instead?** Install Ollama, run `ollama pull llama3.1`, use the Ollama starter, and replace the `ai:` block with:

```yaml
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1
```

### 1.4 Start PostgreSQL

Create `quizforge/docker-compose.yml` now (we'll extend it in Phase 9):

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: quizforge
      POSTGRES_USER: quizforge
      POSTGRES_PASSWORD: quizforge
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quizforge"]
      interval: 5s
      retries: 10

volumes:
  pgdata: {}
```

```bash
docker compose up -d db
```

### 1.5 Delete the generated test

Delete `src/test/java/.../QuizforgeApplicationTests.java`. It boots the whole app (needs DB + API key) and will break `mvn test`. We write focused tests in Phase 7.

### ✅ Check

In IntelliJ, set run-configuration environment variables: `OPENAI_API_KEY`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`. Run `QuizforgeApplication`. It should start on port 8080 (every URL returns 401 for now — that's Spring Security, expected).

---

## Phase 2 — Domain & persistence (≈ 2 h)

Package layout (feature-based):

```
com.harshal.quizforge
├── common/     exceptions, PageResponse, GlobalExceptionHandler
├── auth/       User, Role, security, auth + admin endpoints
└── quiz/       Quiz, Question, Attempt, AI, services, controller
```

### 2.1 `auth/Role.java`

```java
package com.harshal.quizforge.auth;

public enum Role { USER, ADMIN }
```

### 2.2 `auth/User.java`

```java
package com.harshal.quizforge.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")          // "user" is a reserved word in PostgreSQL
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    private Instant createdAt = Instant.now();
}
```

### 2.3 `auth/UserRepository.java`

```java
package com.harshal.quizforge.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

### 2.4 `quiz/Quiz.java`

```java
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
```

### 2.5 `quiz/Question.java`

```java
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
```

### 2.6 `quiz/Attempt.java`

```java
package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;

    private int score;
    private int total;

    private Instant createdAt = Instant.now();
}
```

### 2.7 Repositories

`quiz/QuizRepository.java`:

```java
package com.harshal.quizforge.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Page<Quiz> findByOwnerEmail(String email, Pageable pageable);

    // One query for quiz + owner + questions (options then batch-load).
    @EntityGraph(attributePaths = {"owner", "questions"})
    Optional<Quiz> findWithQuestionsById(Long id);
}
```

`quiz/AttemptRepository.java`:

```java
package com.harshal.quizforge.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    @EntityGraph(attributePaths = "quiz")
    Page<Attempt> findByUserEmail(String email, Pageable pageable);

    void deleteByQuizId(Long quizId);
}
```

### ✅ Check

Restart the app, then:

```bash
docker compose exec db psql -U quizforge -c '\dt'
```

You should see `users`, `quiz`, `question`, `question_options`, `attempt`.

---

## Phase 3 — Common: errors & pagination (≈ 45 min)

### 3.1 Exceptions (`common/`)

```java
package com.harshal.quizforge.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
```

```java
package com.harshal.quizforge.common;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
```

```java
package com.harshal.quizforge.common;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
```

```java
package com.harshal.quizforge.common;

public class AiGenerationException extends RuntimeException {
    public AiGenerationException(String message) { super(message); }
    public AiGenerationException(String message, Throwable cause) { super(message, cause); }
}
```

### 3.2 `common/GlobalExceptionHandler.java`

Returns RFC 7807 `ProblemDetail` JSON — a standard error shape the frontend can rely on.

```java
package com.harshal.quizforge.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(AiGenerationException.class)
    ProblemDetail aiFailed(AiGenerationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        pd.setProperty("errors", errors);
        return pd;
    }
}
```

### 3.3 `common/PageResponse.java`

Spring's `Page` JSON is unstable across versions; return our own shape.

```java
package com.harshal.quizforge.common;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
```

---

## Phase 4 — Security & auth (≈ 3 h)

### 4.1 DTOs — `auth/AuthDtos.java`

```java
package com.harshal.quizforge.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, String email, String role) {}
}
```

> Why `max = 72`? BCrypt ignores bytes after 72 — a classic interview question.

### 4.2 `auth/JwtService.java`

```java
package com.harshal.quizforge.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
    }

    public String generate(String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** Throws io.jsonwebtoken.JwtException if the token is invalid or expired. */
    public String extractEmail(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
```

### 4.3 `auth/AppUserDetailsService.java`

```java
package com.harshal.quizforge.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())     // becomes ROLE_USER / ROLE_ADMIN
                .build();
    }
}
```

### 4.4 `auth/JwtAuthFilter.java`

```java
package com.harshal.quizforge.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String email = jwtService.extractEmail(header.substring(7));
                UserDetails user = userDetailsService.loadUserByUsername(email);
                var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | UsernameNotFoundException e) {
                // Invalid/expired token or deleted user: stay unauthenticated → 401 downstream.
            }
        }
        chain.doFilter(request, response);
    }
}
```

### 4.5 `auth/SecurityConfig.java`

```java
package com.harshal.quizforge.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)   // stateless JWT API, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### 4.6 `auth/AuthService.java`

```java
package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.AuthResponse;
import com.harshal.quizforge.auth.AuthDtos.LoginRequest;
import com.harshal.quizforge.auth.AuthDtos.RegisterRequest;
import com.harshal.quizforge.common.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        users.save(user);
        return tokenFor(user);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        // Throws BadCredentialsException (→ 401 via GlobalExceptionHandler) on wrong email/password.
        authManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        return tokenFor(users.findByEmail(email).orElseThrow());
    }

    private AuthResponse tokenFor(User user) {
        String role = user.getRole().name();
        return new AuthResponse(jwt.generate(user.getEmail(), role), user.getEmail(), role);
    }
}
```

### 4.7 `auth/AuthController.java`

```java
package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.AuthResponse;
import com.harshal.quizforge.auth.AuthDtos.LoginRequest;
import com.harshal.quizforge.auth.AuthDtos.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
```

### 4.8 `auth/AdminSeeder.java` — creates the ADMIN account on startup

```java
package com.harshal.quizforge.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Override
    public void run(String... args) {
        if (email.isBlank() || users.existsByEmail(email.toLowerCase())) return;
        User admin = new User();
        admin.setEmail(email.toLowerCase());
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        users.save(admin);
    }
}
```

### 4.9 `auth/AdminController.java` — the role-based endpoint

```java
package com.harshal.quizforge.auth;

import com.harshal.quizforge.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin")       // ADMIN-only via SecurityConfig
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository users;

    public record UserView(Long id, String email, String role, Instant createdAt) {}

    @GetMapping("/users")
    public PageResponse<UserView> listUsers(Pageable pageable) {
        return PageResponse.of(users.findAll(pageable)
                .map(u -> new UserView(u.getId(), u.getEmail(), u.getRole().name(), u.getCreatedAt())));
    }
}
```

### ✅ Check

```bash
# Register
curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@test.com","password":"password123"}' | jq

# Validation error → 400 with "errors"
curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' -d '{"email":"bad","password":"x"}' | jq

# Duplicate → 409, wrong password → 401
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@test.com","password":"password123"}' | jq -r .token)

# USER hitting admin endpoint → 403
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/admin/users -H "Authorization: Bearer $TOKEN"

# Admin → 200
ADMIN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@quizforge.dev","password":"change-me-please"}' | jq -r .token)
curl -s localhost:8080/api/admin/users -H "Authorization: Bearer $ADMIN" | jq
```

Paste a token into **https://jwt.io** to see the `sub`, `role`, `exp` claims (good to show in interviews).

---

## Phase 5 — AI quiz generation with Spring AI (≈ 2 h)

### 5.1 `quiz/AiQuizGenerator.java`

Spring AI's `.entity(Class)` asks the model for JSON matching the record and parses it for you (structured output).

```java
package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.AiGenerationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiQuizGenerator {

    public record GeneratedQuestion(String prompt, List<String> options, int correctIndex, String explanation) {}
    public record GeneratedQuiz(String title, List<GeneratedQuestion> questions) {}

    private static final String SYSTEM_PROMPT = """
            You are an experienced teacher writing a multiple-choice quiz.
            Use ONLY facts from the user's study material.
            Every question must have exactly 4 options and exactly one correct answer.
            correctIndex is the 0-based index of the correct option.
            Give each question a one-sentence explanation of the correct answer.
            Also give the quiz a short title (max 8 words).
            """;

    private final ChatClient chatClient;

    public AiQuizGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public GeneratedQuiz generate(String sourceText, int questionCount) {
        GeneratedQuiz quiz;
        try {
            quiz = chatClient.prompt()
                    .system(SYSTEM_PROMPT + "Write exactly " + questionCount + " questions.")
                    // Passed as a parameter so braces in the study text aren't treated as template syntax.
                    .user(u -> u.text("Study material:\n{material}").param("material", sourceText))
                    .call()
                    .entity(GeneratedQuiz.class);
        } catch (RuntimeException e) {
            throw new AiGenerationException("AI service failed: " + e.getMessage(), e);
        }
        validate(quiz);
        // Models sometimes return extra questions; keep what was asked for.
        int keep = Math.min(questionCount, quiz.questions().size());
        return new GeneratedQuiz(quiz.title(), List.copyOf(quiz.questions().subList(0, keep)));
    }

    // Never trust LLM output blindly — validate before it touches the database.
    private static void validate(GeneratedQuiz quiz) {
        if (quiz == null || quiz.questions() == null || quiz.questions().isEmpty()) {
            throw new AiGenerationException("AI returned no questions");
        }
        for (GeneratedQuestion q : quiz.questions()) {
            boolean valid = q.prompt() != null && !q.prompt().isBlank()
                    && q.options() != null && q.options().size() == 4
                    && q.correctIndex() >= 0 && q.correctIndex() < 4;
            if (!valid) throw new AiGenerationException("AI returned a malformed question");
        }
    }
}
```

> Spring AI retries transient failures (429/5xx) automatically via `spring.ai.retry.*`.

---

## Phase 6 — Quiz & attempt APIs (≈ 3 h)

### 6.1 DTOs — `quiz/QuizDtos.java`

```java
package com.harshal.quizforge.quiz;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class QuizDtos {
    private QuizDtos() {}

    public record GenerateQuizRequest(
            @Size(max = 120) String title,
            @NotBlank @Size(min = 200, max = 20000, message = "must be 200-20000 characters") String sourceText,
            @Min(3) @Max(15) int questionCount) {}

    public record QuizSummary(Long id, String title, int questionCount, Instant createdAt) {}

    // Sent to the browser while taking a quiz — deliberately has NO correct answers.
    public record QuestionView(Long id, String prompt, List<String> options) {}
    public record QuizDetail(Long id, String title, List<QuestionView> questions) {}

    public record SubmitAttemptRequest(@NotNull List<@NotNull Integer> answers) {}

    public record QuestionResult(Long questionId, int chosen, int correct, boolean isCorrect, String explanation) {}
    public record AttemptResult(Long attemptId, int score, int total, List<QuestionResult> results) {}

    public record AttemptSummary(Long id, Long quizId, String quizTitle, int score, int total, Instant createdAt) {}
}
```

### 6.2 `quiz/Scoring.java` — pure logic, easy to unit test

```java
package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.quiz.QuizDtos.QuestionResult;

import java.util.ArrayList;
import java.util.List;

final class Scoring {
    private Scoring() {}

    static List<QuestionResult> grade(List<Question> questions, List<Integer> answers) {
        if (answers.size() != questions.size()) {
            throw new BadRequestException("Expected " + questions.size() + " answers, got " + answers.size());
        }
        List<QuestionResult> results = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            int chosen = answers.get(i);
            results.add(new QuestionResult(q.getId(), chosen, q.getCorrectIndex(),
                    chosen == q.getCorrectIndex(), q.getExplanation()));
        }
        return results;
    }
}
```

### 6.3 `quiz/QuizService.java`

```java
package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuestion;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuiz;
import com.harshal.quizforge.quiz.QuizDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepo;
    private final UserRepository userRepo;
    private final AttemptRepository attemptRepo;
    private final AiQuizGenerator ai;

    // Deliberately NOT @Transactional: never hold a DB connection while waiting 10-30s on an LLM.
    public QuizSummary generate(String email, GenerateQuizRequest req) {
        GeneratedQuiz generated = ai.generate(req.sourceText(), req.questionCount());
        User owner = userRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Quiz quiz = new Quiz();
        quiz.setTitle(hasText(req.title()) ? req.title().trim() : generated.title());
        quiz.setSourceText(req.sourceText());
        quiz.setOwner(owner);
        for (GeneratedQuestion g : generated.questions()) {
            Question q = new Question();
            q.setPrompt(g.prompt());
            q.setOptions(new ArrayList<>(g.options()));
            q.setCorrectIndex(g.correctIndex());
            q.setExplanation(g.explanation());
            quiz.addQuestion(q);
        }
        quiz.setQuestionCount(quiz.getQuestions().size());
        return toSummary(quizRepo.save(quiz));   // save() runs in its own transaction; questions cascade
    }

    @Transactional(readOnly = true)
    public Page<QuizSummary> list(String email, Pageable pageable) {
        return quizRepo.findByOwnerEmail(email, pageable).map(QuizService::toSummary);
    }

    @Transactional(readOnly = true)
    public QuizDetail getForTaking(String email, Long id) {
        Quiz quiz = getOwnedQuiz(email, id);
        List<QuestionView> questions = quiz.getQuestions().stream()
                .map(q -> new QuestionView(q.getId(), q.getPrompt(), List.copyOf(q.getOptions())))
                .toList();
        return new QuizDetail(quiz.getId(), quiz.getTitle(), questions);
    }

    @Transactional
    public void delete(String email, Long id, boolean isAdmin) {
        Quiz quiz = isAdmin
                ? quizRepo.findById(id).orElseThrow(() -> new NotFoundException("Quiz not found"))
                : getOwnedQuiz(email, id);
        attemptRepo.deleteByQuizId(quiz.getId());   // attempts reference the quiz (FK)
        quizRepo.delete(quiz);
    }

    /** Callers must already be inside a transaction. */
    Quiz getOwnedQuiz(String email, Long id) {
        Quiz quiz = quizRepo.findWithQuestionsById(id)
                .orElseThrow(() -> new NotFoundException("Quiz not found"));
        if (!quiz.getOwner().getEmail().equals(email)) {
            // 404 instead of 403 so users can't probe which quiz IDs exist.
            throw new NotFoundException("Quiz not found");
        }
        return quiz;
    }

    private static QuizSummary toSummary(Quiz q) {
        return new QuizSummary(q.getId(), q.getTitle(), q.getQuestionCount(), q.getCreatedAt());
    }
}
```

### 6.4 `quiz/AttemptService.java`

```java
package com.harshal.quizforge.quiz;

import com.harshal.quizforge.quiz.QuizDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final AttemptRepository attemptRepo;
    private final QuizService quizService;

    @Transactional
    public AttemptResult submit(String email, Long quizId, SubmitAttemptRequest req) {
        Quiz quiz = quizService.getOwnedQuiz(email, quizId);
        // Grading happens on the server: the client never sees correct answers before submitting.
        List<QuestionResult> results = Scoring.grade(quiz.getQuestions(), req.answers());
        int score = (int) results.stream().filter(QuestionResult::isCorrect).count();

        Attempt attempt = new Attempt();
        attempt.setUser(quiz.getOwner());
        attempt.setQuiz(quiz);
        attempt.setScore(score);
        attempt.setTotal(results.size());
        attemptRepo.save(attempt);

        return new AttemptResult(attempt.getId(), score, results.size(), results);
    }

    @Transactional(readOnly = true)
    public Page<AttemptSummary> myAttempts(String email, Pageable pageable) {
        return attemptRepo.findByUserEmail(email, pageable)
                .map(a -> new AttemptSummary(a.getId(), a.getQuiz().getId(), a.getQuiz().getTitle(),
                        a.getScore(), a.getTotal(), a.getCreatedAt()));
    }
}
```

### 6.5 `quiz/QuizController.java`

```java
package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.PageResponse;
import com.harshal.quizforge.quiz.QuizDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizzes;
    private final AttemptService attempts;

    @PostMapping("/quizzes/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizSummary generate(Authentication auth, @Valid @RequestBody GenerateQuizRequest req) {
        return quizzes.generate(auth.getName(), req);
    }

    @GetMapping("/quizzes")
    public PageResponse<QuizSummary> list(Authentication auth,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(quizzes.list(auth.getName(), pageable));
    }

    @GetMapping("/quizzes/{id}")
    public QuizDetail get(Authentication auth, @PathVariable Long id) {
        return quizzes.getForTaking(auth.getName(), id);
    }

    @DeleteMapping("/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        quizzes.delete(auth.getName(), id, isAdmin(auth));
    }

    @PostMapping("/quizzes/{id}/attempts")
    public AttemptResult submit(Authentication auth, @PathVariable Long id,
                                @Valid @RequestBody SubmitAttemptRequest req) {
        return attempts.submit(auth.getName(), id, req);
    }

    @GetMapping("/attempts/me")
    public PageResponse<AttemptSummary> myAttempts(Authentication auth,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(attempts.myAttempts(auth.getName(), pageable));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
```

### ✅ Check — full API flow

Create `sample.json` (any real study notes, ≥ 200 characters):

```json
{
  "title": "",
  "questionCount": 5,
  "sourceText": "Photosynthesis is the process by which green plants use sunlight, water and carbon dioxide to produce glucose and oxygen. It happens mainly in the chloroplasts of leaf cells, which contain the green pigment chlorophyll. The light-dependent reactions occur in the thylakoid membranes and produce ATP and NADPH. The Calvin cycle takes place in the stroma and uses ATP and NADPH to fix carbon dioxide into sugars."
}
```

```bash
# Generate (takes ~5-20s)
curl -s -X POST localhost:8080/api/quizzes/generate \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d @sample.json | jq

# List (paginated)
curl -s 'localhost:8080/api/quizzes?page=0&size=5' -H "Authorization: Bearer $TOKEN" | jq

# Take quiz 1 — confirm there is NO correctIndex in the response
curl -s localhost:8080/api/quizzes/1 -H "Authorization: Bearer $TOKEN" | jq

# Submit answers
curl -s -X POST localhost:8080/api/quizzes/1/attempts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"answers":[0,1,2,3,0]}' | jq

# Wrong number of answers → 400
curl -s -X POST localhost:8080/api/quizzes/1/attempts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"answers":[0]}' | jq

# History
curl -s localhost:8080/api/attempts/me -H "Authorization: Bearer $TOKEN" | jq
```

Commit now: `git add . && git commit -m "Backend: auth, AI quiz generation, attempts"`.

---

## Phase 7 — Tests: JUnit 5 + Mockito (≈ 1.5 h)

`spring-boot-starter-test` (already in your pom) includes JUnit 5, Mockito and AssertJ.

### 7.1 `src/test/java/com/harshal/quizforge/quiz/ScoringTest.java`

```java
package com.harshal.quizforge.quiz;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.quiz.QuizDtos.QuestionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringTest {

    private static Question question(long id, int correctIndex) {
        Question q = new Question();
        q.setId(id);
        q.setCorrectIndex(correctIndex);
        q.setExplanation("because");
        return q;
    }

    @Test
    void gradesEachAnswer() {
        List<QuestionResult> results = Scoring.grade(
                List.of(question(1, 0), question(2, 3)),
                List.of(0, 1));

        assertThat(results).extracting(QuestionResult::isCorrect).containsExactly(true, false);
        assertThat(results.get(1).correct()).isEqualTo(3);
    }

    @Test
    void rejectsWrongNumberOfAnswers() {
        assertThatThrownBy(() -> Scoring.grade(List.of(question(1, 0)), List.of(0, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Expected 1 answers");
    }
}
```

### 7.2 `src/test/java/com/harshal/quizforge/quiz/QuizServiceTest.java`

```java
package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.common.AiGenerationException;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuestion;
import com.harshal.quizforge.quiz.AiQuizGenerator.GeneratedQuiz;
import com.harshal.quizforge.quiz.QuizDtos.GenerateQuizRequest;
import com.harshal.quizforge.quiz.QuizDtos.QuizSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock QuizRepository quizRepo;
    @Mock UserRepository userRepo;
    @Mock AttemptRepository attemptRepo;
    @Mock AiQuizGenerator ai;
    @InjectMocks QuizService service;

    private static final String TEXT = "x".repeat(300);

    private static GeneratedQuestion question() {
        return new GeneratedQuestion("Q?", List.of("a", "b", "c", "d"), 0, "why");
    }

    @Test
    void generate_usesAiTitleWhenNoneGiven_andSavesQuestions() {
        User user = new User();
        user.setEmail("me@test.com");
        when(ai.generate(TEXT, 3)).thenReturn(new GeneratedQuiz("Photosynthesis", List.of(question(), question(), question())));
        when(userRepo.findByEmail("me@test.com")).thenReturn(Optional.of(user));
        when(quizRepo.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizSummary summary = service.generate("me@test.com", new GenerateQuizRequest(" ", TEXT, 3));

        assertThat(summary.title()).isEqualTo("Photosynthesis");
        assertThat(summary.questionCount()).isEqualTo(3);

        ArgumentCaptor<Quiz> saved = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepo).save(saved.capture());
        assertThat(saved.getValue().getOwner()).isSameAs(user);
        assertThat(saved.getValue().getQuestions()).allMatch(q -> q.getQuiz() == saved.getValue());
    }

    @Test
    void generate_savesNothingWhenAiFails() {
        when(ai.generate(anyString(), anyInt())).thenThrow(new AiGenerationException("bad output"));

        assertThatThrownBy(() -> service.generate("me@test.com", new GenerateQuizRequest(null, TEXT, 3)))
                .isInstanceOf(AiGenerationException.class);
        verify(quizRepo, never()).save(any());
    }
}
```

### ✅ Check

```bash
cd backend && ./mvnw test
```

Both test classes green. Commit.

---

## Phase 8 — Frontend: React + TypeScript (≈ 5 h)

### 8.1 Create the app

```bash
cd ~/Desktop/Learn/Java/quizforge
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install @tanstack/react-query react-router-dom axios
```

### 8.2 `vite.config.ts` — proxy `/api` to Spring Boot (no CORS setup needed)

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
});
```

### 8.3 `src/types.ts` — mirrors the backend DTOs

```ts
export type Role = 'USER' | 'ADMIN';
export type AuthResponse = { token: string; email: string; role: Role };

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export type GenerateQuizRequest = { title?: string; sourceText: string; questionCount: number };
export type QuizSummary = { id: number; title: string; questionCount: number; createdAt: string };
export type QuestionView = { id: number; prompt: string; options: string[] };
export type QuizDetail = { id: number; title: string; questions: QuestionView[] };

export type QuestionResult = { questionId: number; chosen: number; correct: number; isCorrect: boolean; explanation: string };
export type AttemptResult = { attemptId: number; score: number; total: number; results: QuestionResult[] };
export type AttemptSummary = { id: number; quizId: number; quizTitle: string; score: number; total: number; createdAt: string };
```

### 8.4 `src/api/client.ts`

```ts
import axios from 'axios';

export const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  response => response,
  error => {
    // Expired/invalid token anywhere except the login form itself → back to login.
    if (error.response?.status === 401 && !error.config?.url?.startsWith('/auth')) {
      localStorage.clear();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  },
);

/** Turns a ProblemDetail error from Spring into a readable message. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data;
    if (data?.errors) return Object.entries(data.errors).map(([field, msg]) => `${field} ${msg}`).join(', ');
    return data?.detail ?? error.message;
  }
  return 'Something went wrong';
}
```

### 8.5 `src/auth/AuthContext.tsx`

```tsx
import { createContext, useContext, useState, type ReactNode } from 'react';
import type { AuthResponse, Role } from '../types';

type User = { email: string; role: Role } | null;
type AuthValue = { user: User; login: (r: AuthResponse) => void; logout: () => void };

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User>(() => {
    const saved = localStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });

  const login = (r: AuthResponse) => {
    const next = { email: r.email, role: r.role };
    localStorage.setItem('token', r.token);
    localStorage.setItem('user', JSON.stringify(next));
    setUser(next);
  };

  const logout = () => {
    localStorage.clear();
    setUser(null);
  };

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}
```

### 8.6 `src/main.tsx`

```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './auth/AuthContext';
import App from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
```

### 8.7 `src/App.tsx` — routing + protected routes

```tsx
import type { ReactNode } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from './auth/AuthContext';
import AuthPage from './pages/AuthPage';
import Dashboard from './pages/Dashboard';
import TakeQuiz from './pages/TakeQuiz';
import History from './pages/History';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user ? <>{children}</> : <Navigate to="/login" replace />;
}

export default function App() {
  const { user, logout } = useAuth();
  const queryClient = useQueryClient();

  return (
    <>
      {user && (
        <nav>
          <Link to="/">My quizzes</Link>
          <Link to="/history">History</Link>
          <span>{user.email}</span>
          {/* Clear cached data so the next user never sees the previous user's quizzes */}
          <button onClick={() => { logout(); queryClient.clear(); }}>Log out</button>
        </nav>
      )}
      <Routes>
        <Route path="/login" element={user ? <Navigate to="/" replace /> : <AuthPage />} />
        <Route path="/" element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/quiz/:id" element={<RequireAuth><TakeQuiz /></RequireAuth>} />
        <Route path="/history" element={<RequireAuth><History /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
```

### 8.8 `src/pages/AuthPage.tsx` — login + register in one form

```tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { AuthResponse } from '../types';

export default function AuthPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();

  const submit = useMutation({
    mutationFn: () => api.post<AuthResponse>(`/auth/${mode}`, { email, password }).then(r => r.data),
    onSuccess: data => {
      login(data);
      navigate('/');
    },
  });

  return (
    <main>
      <h1>{mode === 'login' ? 'Log in' : 'Create account'}</h1>
      <form onSubmit={e => { e.preventDefault(); submit.mutate(); }}>
        <label>
          Email
          <input type="email" required autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} />
        </label>
        <label>
          Password
          <input type="password" required minLength={8} value={password}
                 autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                 onChange={e => setPassword(e.target.value)} />
        </label>
        <button disabled={submit.isPending}>{mode === 'login' ? 'Log in' : 'Sign up'}</button>
        {submit.isError && <p role="alert">{errorMessage(submit.error)}</p>}
      </form>
      <button type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); submit.reset(); }}>
        {mode === 'login' ? 'Need an account? Sign up' : 'Have an account? Log in'}
      </button>
    </main>
  );
}
```

### 8.9 `src/pages/Dashboard.tsx` — generate + paginated list

```tsx
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../api/client';
import type { GenerateQuizRequest, Page, QuizSummary } from '../types';

export default function Dashboard() {
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<GenerateQuizRequest>({ title: '', sourceText: '', questionCount: 5 });
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const quizzes = useQuery({
    queryKey: ['quizzes', page],
    queryFn: () => api.get<Page<QuizSummary>>('/quizzes', { params: { page, size: 10 } }).then(r => r.data),
    placeholderData: keepPreviousData,   // keeps the old page visible while the next one loads
  });

  const generate = useMutation({
    mutationFn: (body: GenerateQuizRequest) => api.post<QuizSummary>('/quizzes/generate', body).then(r => r.data),
    onSuccess: quiz => {
      queryClient.invalidateQueries({ queryKey: ['quizzes'] });
      navigate(`/quiz/${quiz.id}`);
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/quizzes/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['quizzes'] }),
  });

  return (
    <main>
      <h1>Generate a quiz</h1>
      <form onSubmit={e => { e.preventDefault(); generate.mutate(form); }}>
        <label>
          Title (optional)
          <input value={form.title} maxLength={120} onChange={e => setForm({ ...form, title: e.target.value })} />
        </label>
        <label>
          Study material (min 200 characters)
          <textarea required minLength={200} maxLength={20000} rows={10} value={form.sourceText}
                    onChange={e => setForm({ ...form, sourceText: e.target.value })} />
        </label>
        <label>
          Number of questions
          <input type="number" min={3} max={15} value={form.questionCount}
                 onChange={e => setForm({ ...form, questionCount: Number(e.target.value) })} />
        </label>
        <button disabled={generate.isPending}>{generate.isPending ? 'Generating… (up to 30s)' : 'Generate quiz'}</button>
        {generate.isError && <p role="alert">{errorMessage(generate.error)}</p>}
      </form>

      <h2>My quizzes</h2>
      {quizzes.isPending ? (
        <p>Loading…</p>
      ) : quizzes.isError ? (
        <p role="alert">{errorMessage(quizzes.error)}</p>
      ) : quizzes.data.content.length === 0 ? (
        <p>No quizzes yet — generate your first one above.</p>
      ) : (
        <>
          <ul>
            {quizzes.data.content.map(q => (
              <li key={q.id}>
                <Link to={`/quiz/${q.id}`}>{q.title}</Link> — {q.questionCount} questions
                <button onClick={() => remove.mutate(q.id)} aria-label={`Delete ${q.title}`}>Delete</button>
              </li>
            ))}
          </ul>
          <div>
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
            <span> Page {page + 1} of {quizzes.data.totalPages} </span>
            <button disabled={page + 1 >= quizzes.data.totalPages} onClick={() => setPage(p => p + 1)}>Next</button>
          </div>
        </>
      )}
    </main>
  );
}
```

### 8.10 `src/pages/TakeQuiz.tsx`

Uses `fieldset`/`legend` + native radios — fully keyboard and screen-reader accessible (play to your WCAG strength in interviews).

```tsx
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../api/client';
import type { AttemptResult, QuizDetail } from '../types';

export default function TakeQuiz() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [answers, setAnswers] = useState<Record<number, number>>({});

  const quiz = useQuery({
    queryKey: ['quiz', id],
    queryFn: () => api.get<QuizDetail>(`/quizzes/${id}`).then(r => r.data),
  });

  const submit = useMutation({
    mutationFn: (body: { answers: number[] }) =>
      api.post<AttemptResult>(`/quizzes/${id}/attempts`, body).then(r => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['attempts'] }),
  });

  if (quiz.isPending) return <p>Loading…</p>;
  if (quiz.isError) return <p role="alert">{errorMessage(quiz.error)}</p>;

  const { questions } = quiz.data;
  const result = submit.data;
  const allAnswered = questions.every((_, i) => answers[i] !== undefined);

  return (
    <main>
      <h1>{quiz.data.title}</h1>
      {result && <p role="status">You scored {result.score} / {result.total}</p>}

      <form onSubmit={e => { e.preventDefault(); submit.mutate({ answers: questions.map((_, i) => answers[i]) }); }}>
        {questions.map((q, i) => {
          const r = result?.results[i];
          return (
            <fieldset key={q.id} disabled={!!result}>
              <legend>{i + 1}. {q.prompt}</legend>
              {q.options.map((option, j) => (
                <label key={j}>
                  <input type="radio" name={`q-${q.id}`} checked={answers[i] === j}
                         onChange={() => setAnswers({ ...answers, [i]: j })} />
                  {option}
                  {r && j === r.correct && <strong> (correct answer)</strong>}
                </label>
              ))}
              {r && <p>{r.isCorrect ? '✅ Correct.' : '❌ Incorrect.'} {r.explanation}</p>}
            </fieldset>
          );
        })}

        {!result && <button disabled={!allAnswered || submit.isPending}>Submit answers</button>}
        {submit.isError && <p role="alert">{errorMessage(submit.error)}</p>}
      </form>

      {result && <button onClick={() => { setAnswers({}); submit.reset(); }}>Try again</button>}
    </main>
  );
}
```

### 8.11 `src/pages/History.tsx`

```tsx
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../api/client';
import type { AttemptSummary, Page } from '../types';

export default function History() {
  const [page, setPage] = useState(0);
  const attempts = useQuery({
    queryKey: ['attempts', page],
    queryFn: () => api.get<Page<AttemptSummary>>('/attempts/me', { params: { page, size: 10 } }).then(r => r.data),
    placeholderData: keepPreviousData,
  });

  if (attempts.isPending) return <p>Loading…</p>;
  if (attempts.isError) return <p role="alert">{errorMessage(attempts.error)}</p>;

  return (
    <main>
      <h1>My attempts</h1>
      {attempts.data.content.length === 0 ? <p>No attempts yet.</p> : (
        <table>
          <thead><tr><th>Quiz</th><th>Score</th><th>Date</th></tr></thead>
          <tbody>
            {attempts.data.content.map(a => (
              <tr key={a.id}>
                <td><Link to={`/quiz/${a.quizId}`}>{a.quizTitle}</Link></td>
                <td>{a.score} / {a.total} ({Math.round((a.score / a.total) * 100)}%)</td>
                <td>{new Date(a.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
      <button disabled={page + 1 >= attempts.data.totalPages} onClick={() => setPage(p => p + 1)}>Next</button>
    </main>
  );
}
```

### 8.12 Styling

Replace `src/index.css` with something simple and readable (or add Tailwind if you prefer):

```css
:root { font-family: system-ui, sans-serif; line-height: 1.5; color: #1a1a1a; background: #fafafa; }
body { margin: 0; }
nav { display: flex; gap: 1rem; align-items: center; padding: 1rem 2rem; background: #fff; border-bottom: 1px solid #ddd; }
nav span { margin-left: auto; color: #555; }
main { max-width: 760px; margin: 2rem auto; padding: 0 1rem; }
form { display: grid; gap: 1rem; margin-bottom: 2rem; }
label { display: grid; gap: 0.25rem; }
fieldset label { display: flex; gap: 0.5rem; align-items: center; }
input, textarea, button { font: inherit; padding: 0.5rem; }
button { cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: 0.6; }
fieldset { border: 1px solid #ddd; border-radius: 8px; margin-bottom: 1rem; }
[role="alert"] { color: #b00020; }
:focus-visible { outline: 3px solid #2563eb; outline-offset: 2px; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; padding: 0.5rem; border-bottom: 1px solid #eee; }
```

Delete `src/App.css` and the Vite logo assets.

### ✅ Check

Backend running + `npm run dev` → open http://localhost:5173:

1. Sign up → lands on dashboard
2. Paste notes → Generate → redirected to the quiz
3. Answer all → Submit → score + explanations
4. History shows the attempt
5. Delete a quiz → list refreshes
6. Log out → log in as admin works
7. Tab through the quiz with keyboard only

Commit.

---

## Phase 9 — Docker Compose: whole stack in one command (≈ 1.5 h)

### 9.1 `backend/Dockerfile`

```dockerfile
# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline          # cached layer: only re-runs when pom.xml changes
COPY src ./src
RUN mvn -q package -DskipTests

# --- runtime stage (small JRE image, no Maven) ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 9.2 `frontend/nginx.conf`

```nginx
server {
  listen 80;
  root /usr/share/nginx/html;

  location /api/ {
    proxy_pass http://backend:8080;
    proxy_read_timeout 120s;              # AI generation can take a while
  }

  location / {
    try_files $uri /index.html;           # SPA fallback for React Router
  }
}
```

### 9.3 `frontend/Dockerfile`

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
```

Add `frontend/.dockerignore` and `backend/.dockerignore`:

```
node_modules
dist
target
```

### 9.4 Final `docker-compose.yml`

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: quizforge
      POSTGRES_USER: quizforge
      POSTGRES_PASSWORD: quizforge
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quizforge"]
      interval: 5s
      retries: 10

  backend:
    build: ./backend
    environment:
      DB_URL: jdbc:postgresql://db:5432/quizforge
      DB_USER: quizforge
      DB_PASSWORD: quizforge
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      JWT_SECRET: ${JWT_SECRET}
      ADMIN_EMAIL: ${ADMIN_EMAIL}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD}
    depends_on:
      db:
        condition: service_healthy

  frontend:
    build: ./frontend
    ports:
      - "3000:80"
    depends_on:
      - backend

volumes:
  pgdata: {}
```

Compose reads `OPENAI_API_KEY` etc. from the `.env` file next to it automatically.

### ✅ Check

```bash
cd ~/Desktop/Learn/Java/quizforge
docker compose up --build
```

Open http://localhost:3000 and repeat the Phase 8 checklist. Then `docker compose down` (data persists in the `pgdata` volume; `down -v` wipes it).

---

## Phase 10 — Publish (≈ 1.5 h)

1. **README.md** at the repo root with:
   - one-line pitch + 3 screenshots (dashboard, quiz with results, history) or a 30-second GIF
   - tech stack badges/list
   - architecture diagram (copy from section 2)
   - "Run locally": `cp .env.example .env` → fill keys → `docker compose up --build`
   - API table (copy from section 2)
   - "Design decisions" (copy 4–5 bullets from the interview section below)
2. Push to GitHub as **public** repo `quizforge`.
3. Double-check `.env` is NOT in the repo: `git log --all -- .env` should print nothing.
4. Update the resume project heading with the link, e.g.
   `\href{https://github.com/HarshalRaneHR/quizforge}{QuizForge -- AI Quiz \& Learning Tracker}`
5. Change `Spring Boot (fundamentals)` → `Spring Boot` in your skills line.
6. Pin the repo on your GitHub profile.

---

## Interview prep — questions this project will trigger

**Spring / JPA**
- *Why `open-in-view: false`?* Forces data loading inside `@Transactional` services; avoids lazy-loading queries firing during JSON serialization and hidden N+1s.
- *How did you avoid N+1?* `questionCount` column for lists; `@EntityGraph` to fetch quiz + owner + questions in one query; `@BatchSize` on options.
- *Why isn't `generate()` transactional?* The LLM call takes seconds; holding a DB connection that long exhausts the connection pool under load.
- *LAZY vs EAGER?* All `@ManyToOne` set to LAZY; fetch explicitly per use case.
- *`@Transactional(readOnly = true)`?* Hint to Hibernate to skip dirty checking; clearer intent.
- *What does `orphanRemoval = true` do?* Removing a question from the list deletes its row.

**Security**
- *Walk me through a request.* `JwtAuthFilter` reads `Authorization: Bearer`, verifies signature + expiry, loads the user, sets `SecurityContext`; `SecurityFilterChain` enforces `hasRole("ADMIN")` on `/api/admin/**`.
- *Why disable CSRF?* Stateless API using Authorization headers, not cookies — CSRF attacks rely on cookies being sent automatically.
- *JWT in localStorage — risks?* Readable by XSS. Alternative: httpOnly + SameSite cookies (then you need CSRF protection). Mitigations: short expiry, strict CSP.
- *Why return 404 for other users' quizzes?* Doesn't reveal which IDs exist (prevents enumeration).
- *Why are correct answers not in `GET /quizzes/{id}`?* Grading is server-side; the client can't cheat by reading the network tab.
- *How would you revoke a JWT?* Short expiry + refresh tokens, or a deny-list/token version stored per user.

**AI**
- *How do you get structured data from an LLM?* Spring AI `.entity(GeneratedQuiz.class)` adds a JSON schema to the prompt and parses the reply into a record.
- *What if the model returns garbage?* Validate (4 options, index in range, non-empty), convert failures to `AiGenerationException` → HTTP 502, save nothing.
- *Prompt injection?* Study text is passed as a template parameter, the system prompt restricts the task, and output is validated before storage.
- *Cost/latency control?* Small model (`gpt-4o-mini`), `max 20000` chars input, question count capped at 15.

**React**
- *Why TanStack Query instead of Redux here?* This is server state (caching, refetching, invalidation); Redux is for client state. `invalidateQueries` after mutations keeps lists fresh.
- *`keepPreviousData`?* Shows the old page while the next one loads — no flicker in pagination.
- *How do you protect routes?* `RequireAuth` wrapper + axios 401 interceptor for expired tokens.
- *Accessibility?* `fieldset`/`legend` radio groups, `role="alert"` / `role="status"` for results, visible focus styles.

---

## Stretch goals (pick 1–2 after it works)

| Goal | Why it impresses |
|---|---|
| **Flyway** migrations instead of `ddl-auto: update` | Production-grade schema management |
| **Testcontainers** integration test (real PostgreSQL) | Shows you test against real infra |
| **GitHub Actions**: run `./mvnw test` + `npm run build` on every push | CI/CD on your resume becomes provable |
| `@WebMvcTest` controller tests with `spring-security-test` | Tests validation + 401/403 paths |
| Stream AI output (SSE) with progress | Mirrors your Socket.IO work at Brinx |
| Admin page in React (visible only for `role === 'ADMIN'`) | Role-based UI, not just API |
| Upload a PDF instead of pasting text (Apache PDFBox) | Real-world input handling |
| Deploy: backend on Render/Railway, DB on Neon, frontend on Vercel | A live link beats a repo link |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| App won't start: "OpenAI API key must be set" | Set `OPENAI_API_KEY` in the IntelliJ run config / `.env` |
| `IllegalArgumentException` on JWT secret | Secret must be Base64 and ≥ 32 bytes — regenerate with `openssl rand -base64 32` |
| Every request returns 401 | Missing `Bearer ` prefix, expired token, or wrong `JWT_SECRET` after restart |
| 403 on `/api/admin/users` as admin | `ADMIN_EMAIL` user existed before as USER — delete it from DB and restart |
| `LazyInitializationException` | You're touching a lazy field outside a `@Transactional` service method |
| 502 from `/generate` | LLM returned malformed JSON or quota hit — check backend logs; lower `questionCount` |
| Frontend calls hit `localhost:5173/api` and 404 | Backend not running, or `vite.config.ts` proxy missing |
| Port 5432 already in use | Local Postgres running — `brew services stop postgresql` or change the compose port |
| `docker compose up` backend can't reach DB | Use `db` as the host inside compose (already set via `DB_URL`) |

---

## Progress checklist

- [ ] Phase 1 — Skeleton runs, Postgres up
- [ ] Phase 2 — Tables created
- [ ] Phase 3 — Error handling + pagination wrapper
- [ ] Phase 4 — Register/login/JWT, admin 403 vs 200 verified
- [ ] Phase 5 — AI generator returns a valid quiz
- [ ] Phase 6 — Full curl flow works
- [ ] Phase 7 — `./mvnw test` green
- [ ] Phase 8 — UI flow works end-to-end, keyboard accessible
- [ ] Phase 9 — `docker compose up --build` runs everything
- [ ] Phase 10 — Public GitHub repo with README + screenshots, resume link added

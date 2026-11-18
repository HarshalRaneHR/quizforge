package com.harshal.quizforge;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The whole application against a real PostgreSQL (Testcontainers): Flyway migrations, Hibernate
 * schema validation, JSONB columns, native-SQL features (partial unique index), security filters
 * and the main user journeys. Requires Docker.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
        "app.admin.email=admin@test.com",
        "app.admin.password=adminpass123",
        "spring.ai.ollama.base-url=http://localhost:1",   // no model needed for these tests
        "app.jwt.cookie-secure=false",
        "app.auth.public-signup=true",                  // most tests register throwaway learners
        "app.admin.require-password-change=false",      // the seeded admin's forced change is covered by unit tests
})
class QuizForgeIntegrationTest {

    @Container
    @ServiceConnection   // points spring.datasource.* at the container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired MockMvc mvc;

    // ---------------------------------------------------------------- helpers

    private MvcResult send(String path, String token, String json) throws Exception {
        var req = post(path).contentType(MediaType.APPLICATION_JSON).content(json == null ? "" : json);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req).andReturn();
    }

    private String register(String email) throws Exception {
        MvcResult r = send("/api/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    private String login(String email, String password) throws Exception {
        MvcResult r = send("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    /** Admin creates the account; returns {id, temporaryPassword}. */
    private Object[] adminCreates(String admin, String email, String role, String permissionsJson) throws Exception {
        String perms = permissionsJson == null ? "" : ",\"permissions\":" + permissionsJson;
        MvcResult r = send("/api/admin/users", admin,
                "{\"email\":\"" + email + "\",\"fullName\":\"Test Person\",\"role\":\"" + role + "\"" + perms + "}");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String body = r.getResponse().getContentAsString();
        return new Object[]{((Number) JsonPath.read(body, "$.user.id")).longValue(), JsonPath.read(body, "$.temporaryPassword")};
    }

    /** First login with the one-time password, then choose a real one. Returns a usable token. */
    private String firstLogin(String email, String temporaryPassword, String newPassword) throws Exception {
        String pending = login(email, temporaryPassword);
        MvcResult changed = send("/api/account/password", pending,
                "{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"" + newPassword + "\"}");
        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(changed.getResponse().getContentAsString(), "$.token");
    }

    private static String unique(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    private static Number num(MvcResult r, String path) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), path);
    }

    // ---------------------------------------------------------------- tests

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void builtInLibraryIsVisibleReadOnlyAndUndeletable() throws Exception {
        String admin = login("admin@test.com", "adminpass123");
        String learner = register(unique("learner"));

        // Seeded at startup: 5 quizzes of 20 questions, listed in Explore for everyone
        MvcResult explore = mvc.perform(get("/api/quizzes/explore?size=50").header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk()).andReturn();
        List<Integer> libraryIds = JsonPath.read(explore.getResponse().getContentAsString(), "$.content[?(@.builtIn == true)].id");
        List<Integer> counts = JsonPath.read(explore.getResponse().getContentAsString(), "$.content[?(@.builtIn == true)].questionCount");
        assertThat(libraryIds).hasSize(5);
        assertThat(counts).containsOnly(20);
        long javaQuiz = libraryIds.getFirst();

        // Admins can view the answer key (no attempt created); learners can't
        mvc.perform(get("/api/quizzes/" + javaQuiz + "/answer-key").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(20))
                .andExpect(jsonPath("$.questions[0].correctAnswer").exists())
                .andExpect(jsonPath("$.questions[0].explanation").isNotEmpty());
        mvc.perform(get("/api/quizzes/" + javaQuiz + "/answer-key").header("Authorization", "Bearer " + learner))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/quizzes/" + javaQuiz).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.canViewAnswers").value(true))
                .andExpect(jsonPath("$.canAuthor").value(false));   // read-only library: no "Edit in Studio"
        mvc.perform(get("/api/quizzes/" + javaQuiz).header("Authorization", "Bearer " + learner))
                .andExpect(jsonPath("$.canViewAnswers").value(false));

        // Not even an admin can delete or edit it through the API
        mvc.perform(delete("/api/quizzes/" + javaQuiz).header("Authorization", "Bearer " + admin)).andExpect(status().isConflict());
        mvc.perform(put("/api/studio/quizzes/" + javaQuiz).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hacked\"}")).andExpect(status().isForbidden());

        // ...but it can be duplicated into an editable draft
        mvc.perform(post("/api/studio/quizzes/" + javaQuiz + "/duplicate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.questionCount").value(20));

        // Last line of defence: the database trigger refuses a raw DELETE that bypasses the API
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("DELETE FROM quiz WHERE id = ?", javaQuiz))
                .hasMessageContaining("cannot be deleted");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quiz WHERE built_in_slug IS NOT NULL", Integer.class)).isEqualTo(5);
    }

    @Test
    void perUserAccessIsChosenAtCreationAndChangesApplyImmediately() throws Exception {
        String admin = login("admin@test.com", "adminpass123");

        // A learner created WITHOUT AI generation but WITH the Studio
        String email = unique("custom");
        Object[] created = adminCreates(admin, email, "LEARNER", "[\"QUIZ_AUTHOR\"]");
        long userId = (Long) created[0];
        String token = firstLogin(email, (String) created[1], "password123");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.permissions[0]").value("quiz:author"))
                .andExpect(jsonPath("$.permissions.length()").value(1));
        mvc.perform(post("/api/quizzes/generate").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceText\":\"" + "x".repeat(250) + "\",\"blueprint\":{\"counts\":{\"TRUE_FALSE\":1}}}"))
                .andExpect(status().isForbidden());
        assertThat(send("/api/studio/quizzes", token, "{\"title\":\"Mine\"}").getResponse().getStatus()).isEqualTo(201);

        // Admin removes the Studio: the SAME token is refused on its next request
        mvc.perform(put("/api/admin/users/" + userId + "/permissions").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customized").value(true));
        assertThat(send("/api/studio/quizzes", token, "{\"title\":\"Again\"}").getResponse().getStatus()).isEqualTo(403);

        // Admin-only powers can't be handed out one by one
        mvc.perform(put("/api/admin/users/" + userId + "/permissions").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"USER_MANAGE\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inviteOnlyAccountLifecycle() throws Exception {
        String admin = login("admin@test.com", "adminpass123");
        String email = unique("invited");
        Object[] created = adminCreates(admin, email, "LEARNER", null);
        long id = (Long) created[0];
        String temporary = (String) created[1];

        // First login: everything except /api/me and changing the password is refused
        MvcResult first = send("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + temporary + "\"}");
        assertThat((Boolean) JsonPath.read(first.getResponse().getContentAsString(), "$.mustChangePassword")).isTrue();
        String pending = JsonPath.read(first.getResponse().getContentAsString(), "$.token");
        Cookie pendingCookie = first.getResponse().getCookie("qf_refresh");
        mvc.perform(get("/api/quizzes/explore").header("Authorization", "Bearer " + pending))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + pending))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.fullName").value("Test Person"));

        // Wrong current password is a 400 (not 401, which the UI would treat as "session expired")
        assertThat(send("/api/account/password", pending, "{\"currentPassword\":\"nope\",\"newPassword\":\"my-own-pass-1\"}")
                .getResponse().getStatus()).isEqualTo(400);
        MvcResult changed = send("/api/account/password", pending,
                "{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"my-own-pass-1\"}");
        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        assertThat(changed.getResponse().getCookie("qf_refresh")).isNotNull();
        String token = JsonPath.read(changed.getResponse().getContentAsString(), "$.token");
        mvc.perform(get("/api/quizzes/explore").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        // The session that used the temporary password was signed out
        mvc.perform(post("/api/auth/refresh").cookie(pendingCookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users?search=" + email).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].lastLoginAt").isNotEmpty());

        // Deactivate: the same token stops working at once, and login says why only with the right password
        MvcResult loggedIn = send("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"my-own-pass-1\"}");
        Cookie liveCookie = loggedIn.getResponse().getCookie("qf_refresh");
        mvc.perform(post("/api/admin/users/" + id + "/deactivate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEACTIVATED"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(liveCookie)).andExpect(status().isUnauthorized());
        assertThat(send("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"my-own-pass-1\"}")
                .getResponse().getContentAsString()).contains("deactivated");
        assertThat(send("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"wrong-pass-1\"}")
                .getResponse().getStatus()).isEqualTo(401);
        mvc.perform(get("/api/admin/users?status=DEACTIVATED&search=" + email).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.content.length()").value(1));

        // Reactivate, then reset a forgotten password: old one stops working, new one-time password is pending
        mvc.perform(post("/api/admin/users/" + id + "/reactivate").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        login(email, "my-own-pass-1");
        MvcResult reset = send("/api/admin/users/" + id + "/reset-password", admin, null);
        assertThat(reset.getResponse().getStatus()).isEqualTo(200);
        String newTemporary = JsonPath.read(reset.getResponse().getContentAsString(), "$.temporaryPassword");
        assertThat(send("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"my-own-pass-1\"}")
                .getResponse().getStatus()).isEqualTo(401);
        firstLogin(email, newTemporary, "my-own-pass-2");

        // Admins can't lock themselves out
        long adminId = ((Number) JsonPath.read(mvc.perform(get("/api/admin/users?search=admin@test.com")
                .header("Authorization", "Bearer " + admin)).andReturn().getResponse().getContentAsString(), "$.content[0].id")).longValue();
        assertThat(send("/api/admin/users/" + adminId + "/deactivate", admin, null).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        String email = unique("target");
        register(email);
        for (int i = 0; i < 5; i++) {
            assertThat(send("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"guess-" + i + "\"}")
                    .getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult blocked = send("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}");
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void healthIsPublicButApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerRefreshAndLogoutWithRotatingCookie() throws Exception {
        String email = unique("learner");
        MvcResult reg = send("/api/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}");
        Cookie cookie = reg.getResponse().getCookie("qf_refresh");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isOk()).andReturn();
        Cookie rotated = refreshed.getResponse().getCookie("qf_refresh");
        assertThat(rotated.getValue()).isNotEqualTo(cookie.getValue());

        mvc.perform(post("/api/auth/logout").cookie(rotated)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorsAreProblemDetails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void roleBasedAccess() throws Exception {
        String learner = register(unique("learner"));
        String admin = login("admin@test.com", "adminpass123");

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + learner)).andExpect(status().isForbidden());
        mvc.perform(post("/api/studio/quizzes").header("Authorization", "Bearer " + learner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
    }

    @Test
    void instructorPublishesLearnerTakesItAndAnalyticsReflectIt() throws Exception {
        String admin = login("admin@test.com", "adminpass123");
        String teacherEmail = unique("teacher");
        String teacher = firstLogin(teacherEmail, (String) adminCreates(admin, teacherEmail, "INSTRUCTOR", null)[1], "password123");
        String learner = register(unique("learner"));

        // Build: true/false + ordering, pass mark 50%
        long quizId = num(send("/api/studio/quizzes", teacher, "{\"title\":\"Integration quiz\"}"), "$.id").longValue();
        long tf = num(send("/api/studio/quizzes/" + quizId + "/questions", teacher,
                "{\"type\":\"TRUE_FALSE\",\"prompt\":\"Water is wet\",\"answer\":true}"), "$.id").longValue();
        long ord = num(send("/api/studio/quizzes/" + quizId + "/questions", teacher,
                "{\"type\":\"ORDERING\",\"prompt\":\"Order\",\"items\":[\"a\",\"b\",\"c\"]}"), "$.id").longValue();
        mvc.perform(put("/api/studio/quizzes/" + quizId + "/settings").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content("{\"passPercentage\":50}")).andExpect(status().isOk());
        assertThat(send("/api/studio/quizzes/" + quizId + "/publish", teacher, null).getResponse().getStatus()).isEqualTo(200);

        // Learner: landing page has no questions; start, autosave, submit
        mvc.perform(get("/api/quizzes/" + quizId).header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions").doesNotExist());
        MvcResult start = send("/api/quizzes/" + quizId + "/attempts", learner, null);
        long attemptId = num(start, "$.attemptId").longValue();
        assertThat(start.getResponse().getContentAsString()).doesNotContain("correctOrder").doesNotContain("\"answer\"");

        mvc.perform(put("/api/attempts/" + attemptId + "/answers/" + tf).header("Authorization", "Bearer " + learner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"response\":{\"value\":true}}")).andExpect(status().isOk());
        // Resuming returns the same attempt (partial unique index: one open attempt per learner and quiz)
        assertThat(num(send("/api/quizzes/" + quizId + "/attempts", learner, null), "$.attemptId").longValue()).isEqualTo(attemptId);

        MvcResult result = send("/api/attempts/" + attemptId + "/submit", learner, null);
        assertThat(num(result, "$.score").doubleValue()).isEqualTo(1.0);   // TF right, ordering unanswered
        assertThat(num(result, "$.maxScore").doubleValue()).isEqualTo(3.0);
        assertThat((Boolean) JsonPath.read(result.getResponse().getContentAsString(), "$.passed")).isFalse();   // 33% < 50%
        List<Object> results = JsonPath.read(result.getResponse().getContentAsString(), "$.results");
        assertThat(results).hasSize(2);

        // Published quiz with learner attempts is locked
        mvc.perform(post("/api/studio/quizzes/" + quizId + "/unpublish").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isConflict());

        // Analytics see the learner's attempt
        mvc.perform(get("/api/studio/quizzes/" + quizId + "/analytics").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.averagePercentage").value(33));
        mvc.perform(get("/api/analytics/me").header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attempts").value(1));
        assertThat(ord).isPositive();
    }
}

package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.Permission;
import com.harshal.quizforge.auth.Role;
import com.harshal.quizforge.auth.User;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class QuizPolicyTest {

    private static CurrentUser as(String email, Role role) {
        return new CurrentUser(email, role.permissions().stream().map(Permission::authority).collect(Collectors.toSet()));
    }

    private static Quiz quiz(String owner, QuizStatus status, Visibility visibility) {
        User u = new User();
        u.setEmail(owner);
        Quiz q = new Quiz();
        q.setOwner(u);
        q.setStatus(status);
        q.setVisibility(visibility);
        return q;
    }

    private final CurrentUser teacher = as("teacher@test.com", Role.INSTRUCTOR);
    private final CurrentUser learner = as("learner@test.com", Role.LEARNER);
    private final CurrentUser admin = as("admin@test.com", Role.ADMIN);

    @Test
    void learnersSeeOnlyPublishedPublicQuizzes() {
        assertThat(QuizPolicy.canTake(quiz("teacher@test.com", QuizStatus.PUBLISHED, Visibility.PUBLIC), learner)).isTrue();
        assertThat(QuizPolicy.canTake(quiz("teacher@test.com", QuizStatus.DRAFT, Visibility.PUBLIC), learner)).isFalse();
        assertThat(QuizPolicy.canTake(quiz("teacher@test.com", QuizStatus.ARCHIVED, Visibility.PUBLIC), learner)).isFalse();
        assertThat(QuizPolicy.canTake(quiz("other@test.com", QuizStatus.PUBLISHED, Visibility.PRIVATE), learner)).isFalse();
    }

    @Test
    void ownersCanPreviewTheirDrafts() {
        assertThat(QuizPolicy.canTake(quiz("teacher@test.com", QuizStatus.DRAFT, Visibility.PUBLIC), teacher)).isTrue();
    }

    @Test
    void onlyOwningInstructorsOrAdminsCanAuthor() {
        Quiz q = quiz("teacher@test.com", QuizStatus.DRAFT, Visibility.PUBLIC);
        assertThat(QuizPolicy.canAuthor(q, teacher)).isTrue();
        assertThat(QuizPolicy.canAuthor(q, as("other-teacher@test.com", Role.INSTRUCTOR))).isFalse();
        assertThat(QuizPolicy.canAuthor(q, admin)).isTrue();
        // a learner who somehow owns a quiz still lacks quiz:author
        assertThat(QuizPolicy.canAuthor(quiz("learner@test.com", QuizStatus.DRAFT, Visibility.PUBLIC), learner)).isFalse();
    }

    @Test
    void adminCanSeeEverything() {
        assertThat(QuizPolicy.canTake(quiz("x@test.com", QuizStatus.DRAFT, Visibility.PRIVATE), admin)).isTrue();
        assertThat(QuizPolicy.canDelete(quiz("x@test.com", QuizStatus.DRAFT, Visibility.PRIVATE), admin)).isTrue();
        assertThat(QuizPolicy.canDelete(quiz("x@test.com", QuizStatus.DRAFT, Visibility.PRIVATE), new CurrentUser("y@test.com", Set.of()))).isFalse();
    }
}

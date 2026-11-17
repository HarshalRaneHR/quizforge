package com.harshal.quizforge.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void learnerCanOnlyGeneratePracticeQuizzes() {
        assertThat(Role.LEARNER.permissions()).containsExactly(Permission.QUIZ_GENERATE);
    }

    @Test
    void instructorAuthorsButCannotManageUsers() {
        assertThat(Role.INSTRUCTOR.has(Permission.QUIZ_AUTHOR)).isTrue();
        assertThat(Role.INSTRUCTOR.has(Permission.USER_MANAGE)).isFalse();
        assertThat(Role.INSTRUCTOR.has(Permission.QUIZ_MANAGE_ALL)).isFalse();
    }

    @Test
    void adminHasEveryPermission() {
        assertThat(Role.ADMIN.permissions()).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void authoritiesIncludeRoleAndPermissionStrings() {
        assertThat(Role.INSTRUCTOR.authorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_INSTRUCTOR", "quiz:author", "classroom:manage")
                .doesNotContain("user:manage");
    }
}

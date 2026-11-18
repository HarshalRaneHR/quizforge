package com.harshal.quizforge.auth;

import com.harshal.quizforge.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Per-user access: role defaults ± overrides, and the rules for changing them. */
@ExtendWith(MockitoExtension.class)
class UserAccessTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock RefreshTokenService refreshTokens;
    @InjectMocks AdminUserService service;

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setRole(role);
        return u;
    }

    @Test
    void effectivePermissionsAreRoleDefaultsPlusOverrides() {
        User learner = user(1, Role.LEARNER);
        assertThat(learner.effectivePermissions()).containsExactly(Permission.QUIZ_GENERATE);

        learner.setAccess(EnumSet.of(Permission.QUIZ_AUTHOR));   // remove generate, add author
        assertThat(learner.effectivePermissions()).containsExactly(Permission.QUIZ_AUTHOR);
        assertThat(learner.getPermissionOverrides())
                .containsEntry(Permission.QUIZ_GENERATE, false)
                .containsEntry(Permission.QUIZ_AUTHOR, true)
                .hasSize(2);   // only differences from the defaults are stored
    }

    @Test
    void choosingExactlyTheDefaultsStoresNoOverrides() {
        User teacher = user(1, Role.INSTRUCTOR);
        teacher.setAccess(Role.INSTRUCTOR.permissions());
        assertThat(teacher.getPermissionOverrides()).isEmpty();
    }

    @Test
    void createLearnerWithoutAiGeneration() {
        when(users.existsByEmail(any())).thenReturn(false);
        when(encoder.encode(any())).thenReturn("HASH");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var view = service.createUser("kid@test.com", "Kid", Role.LEARNER, Set.of()).user();

        assertThat(view.permissions()).isEmpty();
        assertThat(view.customized()).isTrue();
    }

    @Test
    void adminOnlyPowersCannotBeGrantedIndividually() {
        when(users.findById(2L)).thenReturn(Optional.of(user(2, Role.INSTRUCTOR)));

        assertThatThrownBy(() -> service.setAccess("admin@test.com", 2L, Set.of(Permission.USER_MANAGE)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Admin role");
    }

    @Test
    void adminsAlwaysKeepFullAccess() {
        when(users.findById(3L)).thenReturn(Optional.of(user(3, Role.ADMIN)));

        assertThatThrownBy(() -> service.setAccess("admin@test.com", 3L, Set.of()))
                .isInstanceOf(BadRequestException.class);
        assertThat(user(3, Role.ADMIN).effectivePermissions()).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void cannotChangeOwnAccess() {
        User me = user(4, Role.INSTRUCTOR);
        me.setEmail("admin@test.com");
        when(users.findById(4L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.setAccess("admin@test.com", 4L, Set.of())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void changingRoleResetsToTheNewRolesDefaults() {
        User learner = user(5, Role.LEARNER);
        learner.setAccess(EnumSet.of(Permission.QUIZ_AUTHOR));
        when(users.findById(5L)).thenReturn(Optional.of(learner));

        var view = service.changeRole("admin@test.com", 5L, Role.INSTRUCTOR);

        assertThat(learner.getPermissionOverrides()).isEmpty();
        assertThat(view.permissions()).containsExactlyInAnyOrderElementsOf(Role.INSTRUCTOR.permissions());
        assertThat(view.customized()).isFalse();
    }
}

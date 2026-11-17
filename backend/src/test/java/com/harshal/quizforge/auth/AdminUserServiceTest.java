package com.harshal.quizforge.auth;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository users;
    @Mock org.springframework.security.crypto.password.PasswordEncoder encoder;
    @InjectMocks AdminUserService service;

    private static User user(long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        return u;
    }

    @Test
    void promotesLearnerToInstructor() {
        User learner = user(2, "learner@test.com", Role.LEARNER);
        when(users.findById(2L)).thenReturn(Optional.of(learner));

        var view = service.changeRole("admin@test.com", 2L, Role.INSTRUCTOR);

        assertThat(learner.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(view.role()).isEqualTo("INSTRUCTOR");
    }

    @Test
    void adminCannotChangeOwnRole() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, "admin@test.com", Role.ADMIN)));

        assertThatThrownBy(() -> service.changeRole("admin@test.com", 1L, Role.LEARNER))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createsInstructorWithHashedPassword() {
        when(users.existsByEmail("teacher@test.com")).thenReturn(false);
        when(encoder.encode("secret123")).thenReturn("HASH");
        when(users.save(org.mockito.ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var view = service.createUser("  Teacher@Test.com ", "secret123", Role.INSTRUCTOR);

        assertThat(view.email()).isEqualTo("teacher@test.com");
        assertThat(view.role()).isEqualTo("INSTRUCTOR");
        org.mockito.Mockito.verify(users).save(org.mockito.ArgumentMatchers.argThat(u -> "HASH".equals(u.getPasswordHash())));
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(users.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser("dup@test.com", "secret123", Role.LEARNER))
                .isInstanceOf(com.harshal.quizforge.common.ConflictException.class);
    }

    @Test
    void unknownUserIsNotFound() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole("admin@test.com", 99L, Role.LEARNER))
                .isInstanceOf(NotFoundException.class);
    }
}

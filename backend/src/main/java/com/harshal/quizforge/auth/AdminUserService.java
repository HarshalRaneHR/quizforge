package com.harshal.quizforge.auth;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.harshal.quizforge.auth.UserRepository.Specs.emailContains;
import static com.harshal.quizforge.auth.UserRepository.Specs.hasRole;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;

    public record UserView(Long id, String email, String role, Instant createdAt) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getRole().name(), u.getCreatedAt());
        }
    }

    @PreAuthorize("hasAuthority('user:read')")
    @Transactional(readOnly = true)
    public Page<UserView> search(String emailFilter, Role roleFilter, Pageable pageable) {
        return users.findAll(emailContains(emailFilter).and(hasRole(roleFilter)), pageable).map(UserView::of);
    }

    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public UserView changeRole(String actingEmail, Long userId, Role newRole) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        // Stops an admin from accidentally demoting themselves and locking everyone out.
        if (user.getEmail().equals(actingEmail)) {
            throw new BadRequestException("You can't change your own role");
        }
        user.setRole(newRole);   // dirty checking saves it at commit
        return UserView.of(user);
    }
}

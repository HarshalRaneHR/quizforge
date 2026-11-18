package com.harshal.quizforge.auth;

/**
 * What the admin sees: PENDING = active but the person hasn't chosen their own password yet
 * (new account or after a reset).
 */
public enum AccountState {
    ACTIVE, PENDING, DEACTIVATED;

    static AccountState of(User user) {
        if (user.getStatus() == AccountStatus.DEACTIVATED) return DEACTIVATED;
        return user.isMustChangePassword() ? PENDING : ACTIVE;
    }
}

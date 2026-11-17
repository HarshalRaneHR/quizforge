package com.harshal.quizforge.quiz;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.Permission;

/**
 * Who may do what with a quiz — every rule in one place, pure functions, easy to unit test.
 * (Spring Security checks the coarse permission per endpoint; these check ownership and state.)
 */
public final class QuizPolicy {
    private QuizPolicy() {}

    public static boolean isOwner(Quiz quiz, CurrentUser user) {
        return quiz.getOwner().getEmail().equals(user.email());
    }

    /** Owners can always open their own quiz (including drafts as a preview); others only listed ones. */
    public static boolean canTake(Quiz quiz, CurrentUser user) {
        return isOwner(quiz, user) || quiz.isListed() || user.can(Permission.QUIZ_MANAGE_ALL);
    }

    /** Edit metadata, questions and lifecycle in the Studio. */
    public static boolean canAuthor(Quiz quiz, CurrentUser user) {
        return (isOwner(quiz, user) && user.can(Permission.QUIZ_AUTHOR)) || user.can(Permission.QUIZ_MANAGE_ALL);
    }

    public static boolean canDelete(Quiz quiz, CurrentUser user) {
        return isOwner(quiz, user) || user.can(Permission.QUIZ_MANAGE_ALL);
    }
}

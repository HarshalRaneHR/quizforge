package com.harshal.quizforge.auth;

/**
 * Fine-grained capabilities. Roles are just named bundles of permissions (see {@link Role}),
 * and endpoints check permissions — so changing what a role can do is a one-line change here.
 */
public enum Permission {
    /** Generate personal practice quizzes from your own notes. */
    QUIZ_GENERATE("quiz:generate"),
    /** Create, edit and publish quizzes for other people (quiz builder, Phase C). */
    QUIZ_AUTHOR("quiz:author"),
    /** Edit or delete any user's quiz. */
    QUIZ_MANAGE_ALL("quiz:manage-all"),
    /** Create classrooms and assign quizzes (Phase E). */
    CLASSROOM_MANAGE("classroom:manage"),
    /** List and view users. */
    USER_READ("user:read"),
    /** Change users' roles, enable/disable accounts. */
    USER_MANAGE("user:manage");

    private final String authority;

    Permission(String authority) {
        this.authority = authority;
    }

    /** The string Spring Security checks, e.g. {@code @PreAuthorize("hasAuthority('user:read')")}. */
    public String authority() {
        return authority;
    }
}

package com.harshal.quizforge.auth;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fine-grained capabilities. Roles are named bundles of permissions (see {@link Role}); endpoints
 * check permissions. Admins can additionally turn the {@link #adjustable() adjustable} ones on or off
 * per user; admin-only powers always come from the ADMIN role.
 */
public enum Permission {
    /** Generate personal practice quizzes from your own notes. */
    QUIZ_GENERATE("quiz:generate", true),
    /** Create, edit and publish quizzes for other people (Studio). */
    QUIZ_AUTHOR("quiz:author", true),
    /** Edit or delete any user's quiz. Admin only. */
    QUIZ_MANAGE_ALL("quiz:manage-all", false),
    /** Create classrooms and assign quizzes. */
    CLASSROOM_MANAGE("classroom:manage", true),
    /** List and view users. Admin only. */
    USER_READ("user:read", false),
    /** Create users, change roles and access. Admin only. */
    USER_MANAGE("user:manage", false);

    private final String authority;
    private final boolean adjustable;

    Permission(String authority, boolean adjustable) {
        this.authority = authority;
        this.adjustable = adjustable;
    }

    /** The string Spring Security checks, e.g. {@code @PreAuthorize("hasAuthority('user:read')")}. */
    public String authority() {
        return authority;
    }

    /** Can an admin grant or remove this for an individual (non-admin) user? */
    public boolean adjustable() {
        return adjustable;
    }

    public static Set<Permission> adjustableOnes() {
        return Arrays.stream(values()).filter(Permission::adjustable).collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }
}

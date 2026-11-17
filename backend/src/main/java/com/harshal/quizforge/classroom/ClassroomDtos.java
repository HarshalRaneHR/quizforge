package com.harshal.quizforge.classroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ClassroomDtos {
    private ClassroomDtos() {}

    public record SaveClassroomRequest(@NotBlank @Size(max = 120) String name, @Size(max = 2000) String description) {}

    public record JoinRequest(@NotBlank @Size(max = 12) String code) {}

    public record AssignRequest(@NotNull Long quizId, Instant dueAt) {}

    /** joinCode is only included for the classroom's owner. */
    public record ClassroomSummary(Long id, String name, String description, String owner, int memberCount,
                                   int assignmentCount, String joinCode, Instant createdAt) {}

    public record MemberView(Long userId, String email, Instant joinedAt) {}

    public record AssignmentView(Long id, Long quizId, String quizTitle, int questionCount, int totalPoints,
                                 Instant dueAt, Instant createdAt) {}

    /** Members and the join code are only included for the owner. */
    public record ClassroomDetail(Long id, String name, String description, String owner, boolean owned, String joinCode,
                                  List<MemberView> members, List<AssignmentView> assignments) {}

    public enum Progress { NOT_STARTED, IN_PROGRESS, COMPLETED }

    /** A learner's view of one assignment. */
    public record MyAssignment(Long assignmentId, Long classroomId, String classroomName, Long quizId, String quizTitle,
                               int questionCount, Instant dueAt, boolean overdue, Progress progress,
                               Integer bestPercentage, int attempts) {}

    public record GradebookCell(Long assignmentId, Progress progress, Integer bestPercentage, int attempts) {}

    public record GradebookRow(Long userId, String email, List<GradebookCell> cells, Integer averagePercentage) {}

    public record Gradebook(Long classroomId, String classroomName, List<AssignmentView> assignments, List<GradebookRow> rows) {}
}

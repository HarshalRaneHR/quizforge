package com.harshal.quizforge.classroom;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.auth.Permission;
import com.harshal.quizforge.auth.User;
import com.harshal.quizforge.auth.UserRepository;
import com.harshal.quizforge.classroom.ClassroomDtos.*;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.ForbiddenException;
import com.harshal.quizforge.common.NotFoundException;
import com.harshal.quizforge.quiz.AttemptRepository;
import com.harshal.quizforge.quiz.AttemptRepository.AttemptStats;
import com.harshal.quizforge.quiz.Quiz;
import com.harshal.quizforge.quiz.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

/** Classrooms: instructors create them and assign published quizzes; learners join with a code. */
@Service
@RequiredArgsConstructor
public class ClassroomService {

    /** No 0/O, 1/I/L: codes are read aloud and typed by hand. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClassroomRepository classrooms;
    private final AssignmentRepository assignments;
    private final UserRepository users;
    private final QuizRepository quizzes;
    private final AttemptRepository attempts;
    private final Clock clock;

    // ---------------------------------------------------------------- instructor

    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional
    public ClassroomDetail create(CurrentUser user, SaveClassroomRequest req) {
        Classroom c = new Classroom();
        c.setOwner(user(user));
        c.setName(req.name().trim());
        c.setDescription(hasText(req.description()) ? req.description().trim() : null);
        c.setJoinCode(newJoinCode());
        c.setCreatedAt(clock.instant());
        return detail(classrooms.save(c), user);
    }

    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional(readOnly = true)
    public List<ClassroomSummary> listOwned(CurrentUser user) {
        return classrooms.findByOwnerEmailOrderByCreatedAtDesc(user.email()).stream().map(c -> summary(c, user)).toList();
    }

    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional
    public ClassroomDetail update(CurrentUser user, Long id, SaveClassroomRequest req) {
        Classroom c = managed(user, id);
        c.setName(req.name().trim());
        c.setDescription(hasText(req.description()) ? req.description().trim() : null);
        return detail(c, user);
    }

    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional
    public void delete(CurrentUser user, Long id) {
        classrooms.delete(managed(user, id));
    }

    /** New code (e.g. the old one was shared too widely); existing members stay. */
    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional
    public ClassroomDetail regenerateCode(CurrentUser user, Long id) {
        Classroom c = managed(user, id);
        c.setJoinCode(newJoinCode());
        return detail(c, user);
    }

    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional
    public void removeMember(CurrentUser user, Long id, Long userId) {
        Classroom c = managed(user, id);
        if (!c.getMembers().removeIf(m -> m.getUser().getId().equals(userId))) {
            throw new NotFoundException("Not a member of this classroom");
        }
    }

    /** Only quizzes that learners can take (published, public) and that you own. */
    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional
    public AssignmentView assign(CurrentUser user, Long id, AssignRequest req) {
        Classroom c = managed(user, id);
        Quiz quiz = quizzes.findById(req.quizId()).orElseThrow(() -> new NotFoundException("Quiz not found"));
        // Your own quizzes, or any built-in library quiz.
        boolean ownsQuiz = quiz.isBuiltIn() || quiz.getOwner().getEmail().equals(user.email()) || user.can(Permission.QUIZ_MANAGE_ALL);
        if (!ownsQuiz) throw new NotFoundException("Quiz not found");
        if (!quiz.isListed()) throw new BadRequestException("Only published quizzes can be assigned");
        if (c.getAssignments().stream().anyMatch(a -> a.getQuiz().getId().equals(quiz.getId()))) {
            throw new ConflictException("This quiz is already assigned to the classroom");
        }
        Assignment a = new Assignment();
        a.setClassroom(c);
        a.setQuiz(quiz);
        a.setDueAt(req.dueAt());
        a.setCreatedAt(clock.instant());
        c.getAssignments().add(a);
        assignments.save(a);
        return view(a);
    }

    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional
    public void unassign(CurrentUser user, Long id, Long assignmentId) {
        Classroom c = managed(user, id);
        if (!c.getAssignments().removeIf(a -> a.getId().equals(assignmentId))) {
            throw new NotFoundException("Assignment not found");
        }
    }

    /** Learners × assignments: best score, attempts and progress for each cell. */
    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional(readOnly = true)
    public Gradebook gradebook(CurrentUser user, Long id) {
        Classroom c = managed(user, id);
        List<Assignment> as = c.getAssignments();
        List<User> learners = c.getMembers().stream().map(ClassroomMember::getUser).toList();
        Map<String, AttemptStats> stats = stats(as.stream().map(a -> a.getQuiz().getId()).toList(),
                learners.stream().map(User::getId).toList());

        List<GradebookRow> rows = learners.stream().map(u -> {
            List<GradebookCell> cells = as.stream().map(a -> {
                AttemptStats s = stats.get(key(a.getQuiz().getId(), u.getId()));
                return new GradebookCell(a.getId(), progress(s), best(s), s == null ? 0 : s.getSubmitted().intValue());
            }).toList();
            OptionalDouble avg = cells.stream().filter(x -> x.bestPercentage() != null).mapToInt(GradebookCell::bestPercentage).average();
            return new GradebookRow(u.getId(), u.getEmail(), u.getFullName(), !u.isActive(), cells,
                    avg.isPresent() ? (int) Math.round(avg.getAsDouble()) : null);
        }).toList();
        return new Gradebook(c.getId(), c.getName(), as.stream().map(ClassroomService::view).toList(), rows);
    }

    /** Spreadsheet-friendly export of the gradebook (best % per assignment). */
    @PreAuthorize("hasAuthority('classroom:manage')")
    @Transactional(readOnly = true)
    public String gradebookCsv(CurrentUser user, Long id) {
        Gradebook g = gradebook(user, id);
        StringBuilder csv = new StringBuilder();
        List<String> header = new ArrayList<>(List.of("Name", "Email"));
        g.assignments().forEach(a -> header.add(a.quizTitle()));
        header.add("Average");
        csv.append(header.stream().map(ClassroomService::csvCell).collect(Collectors.joining(","))).append("\r\n");
        for (GradebookRow row : g.rows()) {
            String name = row.fullName() == null ? "" : row.fullName();
            List<String> line = new ArrayList<>(List.of(row.deactivated() ? name + " (deactivated)" : name, row.email()));
            row.cells().forEach(cell -> line.add(cell.bestPercentage() == null ? "" : String.valueOf(cell.bestPercentage())));
            line.add(row.averagePercentage() == null ? "" : String.valueOf(row.averagePercentage()));
            csv.append(line.stream().map(ClassroomService::csvCell).collect(Collectors.joining(","))).append("\r\n");
        }
        return csv.toString();
    }

    // ---------------------------------------------------------------- learner

    /** Joining twice is harmless (returns the classroom). Owners can't join their own class. */
    @Transactional
    public ClassroomDetail join(CurrentUser user, String code) {
        Classroom c = classrooms.findByJoinCode(code.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("No classroom with that code"));
        if (c.getOwner().getEmail().equals(user.email())) throw new BadRequestException("You own this classroom");
        boolean already = c.getMembers().stream().anyMatch(m -> m.getUser().getEmail().equals(user.email()));
        if (!already) {
            ClassroomMember m = new ClassroomMember();
            m.setClassroom(c);
            m.setUser(user(user));
            m.setJoinedAt(clock.instant());
            c.getMembers().add(m);
        }
        return detail(c, user);
    }

    @Transactional
    public void leave(CurrentUser user, Long id) {
        Classroom c = visible(user, id);
        c.getMembers().removeIf(m -> m.getUser().getEmail().equals(user.email()));
    }

    @Transactional(readOnly = true)
    public List<ClassroomSummary> listJoined(CurrentUser user) {
        return classrooms.findJoinedBy(user.email()).stream().map(c -> summary(c, user)).toList();
    }

    @Transactional(readOnly = true)
    public ClassroomDetail get(CurrentUser user, Long id) {
        return detail(visible(user, id), user);
    }

    /** Everything assigned to me across my classrooms, soonest due first, with my progress. */
    @Transactional(readOnly = true)
    public List<MyAssignment> myAssignments(CurrentUser user) {
        List<Assignment> list = assignments.findForLearner(user.email()).stream()
                .filter(a -> a.getQuiz().isListed())   // archived quizzes drop off the list
                .toList();
        if (list.isEmpty()) return List.of();
        Long me = user(user).getId();
        Map<String, AttemptStats> stats = stats(list.stream().map(a -> a.getQuiz().getId()).distinct().toList(), List.of(me));
        Instant now = clock.instant();
        return list.stream().map(a -> {
            AttemptStats s = stats.get(key(a.getQuiz().getId(), me));
            Progress p = progress(s);
            boolean overdue = a.getDueAt() != null && now.isAfter(a.getDueAt()) && p != Progress.COMPLETED;
            return new MyAssignment(a.getId(), a.getClassroom().getId(), a.getClassroom().getName(), a.getQuiz().getId(),
                    a.getQuiz().getTitle(), a.getQuiz().getQuestionCount(), a.getDueAt(), overdue, p, best(s),
                    s == null ? 0 : s.getSubmitted().intValue());
        }).toList();
    }

    // ---------------------------------------------------------------- helpers

    private Classroom load(Long id) {
        return classrooms.findWithOwnerById(id).orElseThrow(() -> new NotFoundException("Classroom not found"));
    }

    private static boolean owns(Classroom c, CurrentUser user) {
        return c.getOwner().getEmail().equals(user.email()) || user.can(Permission.QUIZ_MANAGE_ALL);
    }

    private static boolean isMember(Classroom c, CurrentUser user) {
        return c.getMembers().stream().anyMatch(m -> m.getUser().getEmail().equals(user.email()));
    }

    /** Owner (or admin) only. Others get 404 if they aren't members (don't reveal it exists), else 403. */
    private Classroom managed(CurrentUser user, Long id) {
        Classroom c = load(id);
        if (owns(c, user)) return c;
        if (isMember(c, user)) throw new ForbiddenException("Only the classroom's instructor can do that");
        throw new NotFoundException("Classroom not found");
    }

    private Classroom visible(CurrentUser user, Long id) {
        Classroom c = load(id);
        if (!owns(c, user) && !isMember(c, user)) throw new NotFoundException("Classroom not found");
        return c;
    }

    private User user(CurrentUser user) {
        return users.findByEmail(user.email()).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String newJoinCode() {
        for (int tries = 0; tries < 10; tries++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            if (!classrooms.existsByJoinCode(code.toString())) return code.toString();
        }
        throw new IllegalStateException("Could not generate a unique join code");
    }

    private Map<String, AttemptStats> stats(List<Long> quizIds, List<Long> userIds) {
        if (quizIds.isEmpty() || userIds.isEmpty()) return Map.of();
        return attempts.statsFor(quizIds, userIds).stream()
                .collect(Collectors.toMap(s -> key(s.getQuizId(), s.getUserId()), s -> s));
    }

    private static String key(Long quizId, Long userId) {
        return quizId + ":" + userId;
    }

    static Progress progress(AttemptStats s) {
        if (s == null) return Progress.NOT_STARTED;
        if (s.getSubmitted() > 0) return Progress.COMPLETED;
        return s.getOpen() > 0 ? Progress.IN_PROGRESS : Progress.NOT_STARTED;
    }

    static Integer best(AttemptStats s) {
        return s == null || s.getBest() == null ? null : (int) Math.round(s.getBest() * 100);
    }

    /** Quote cells containing separators; neutralise leading =,+,-,@ so spreadsheets don't run them as formulas. */
    static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        boolean quote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        return quote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static AssignmentView view(Assignment a) {
        Quiz q = a.getQuiz();
        return new AssignmentView(a.getId(), q.getId(), q.getTitle(), q.getQuestionCount(), q.getTotalPoints(), a.getDueAt(), a.getCreatedAt());
    }

    private ClassroomSummary summary(Classroom c, CurrentUser user) {
        boolean owner = owns(c, user);
        return new ClassroomSummary(c.getId(), c.getName(), c.getDescription(), c.getOwner().getEmail(),
                c.getMembers().size(), c.getAssignments().size(), owner ? c.getJoinCode() : null, c.getCreatedAt());
    }

    private ClassroomDetail detail(Classroom c, CurrentUser user) {
        boolean owner = owns(c, user);
        List<MemberView> members = owner
                ? c.getMembers().stream().map(m -> new MemberView(m.getUser().getId(), m.getUser().getEmail(), m.getUser().getFullName(),
                        !m.getUser().isActive(), m.getJoinedAt())).toList()
                : List.of();
        List<AssignmentView> as = c.getAssignments().stream()
                .filter(a -> owner || a.getQuiz().isListed())
                .map(ClassroomService::view).toList();
        return new ClassroomDetail(c.getId(), c.getName(), c.getDescription(), c.getOwner().getEmail(), owner,
                owner ? c.getJoinCode() : null, members, as);
    }
}

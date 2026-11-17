package com.harshal.quizforge.classroom;

import com.harshal.quizforge.auth.CurrentUser;
import com.harshal.quizforge.classroom.ClassroomDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Classrooms", description = "Instructors run classes and assign quizzes; learners join with a code")
public class ClassroomController {

    private final ClassroomService service;

    // ---- learners

    @Operation(summary = "Join a classroom with its code")
    @PostMapping("/classrooms/join")
    public ClassroomDetail join(@Parameter(hidden = true) Authentication auth, @Valid @RequestBody JoinRequest req) {
        return service.join(CurrentUser.of(auth), req.code());
    }

    @Operation(summary = "Classrooms I've joined")
    @GetMapping("/classrooms/joined")
    public List<ClassroomSummary> joined(@Parameter(hidden = true) Authentication auth) {
        return service.listJoined(CurrentUser.of(auth));
    }

    @Operation(summary = "Leave a classroom")
    @PostMapping("/classrooms/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        service.leave(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Quizzes assigned to me", description = "Across all my classrooms, soonest due first, with my progress.")
    @GetMapping("/assignments/me")
    public List<MyAssignment> myAssignments(@Parameter(hidden = true) Authentication auth) {
        return service.myAssignments(CurrentUser.of(auth));
    }

    @Operation(summary = "A classroom I own or belong to", description = "Members and join code only for the owner.")
    @GetMapping("/classrooms/{id}")
    public ClassroomDetail get(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return service.get(CurrentUser.of(auth), id);
    }

    // ---- instructors (classroom:manage)

    @Operation(summary = "Create a classroom (gets a join code)")
    @PostMapping("/classrooms")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassroomDetail create(@Parameter(hidden = true) Authentication auth, @Valid @RequestBody SaveClassroomRequest req) {
        return service.create(CurrentUser.of(auth), req);
    }

    @Operation(summary = "Classrooms I teach")
    @GetMapping("/classrooms")
    public List<ClassroomSummary> owned(@Parameter(hidden = true) Authentication auth) {
        return service.listOwned(CurrentUser.of(auth));
    }

    @Operation(summary = "Rename / describe a classroom")
    @PutMapping("/classrooms/{id}")
    public ClassroomDetail update(@Parameter(hidden = true) Authentication auth, @PathVariable Long id, @Valid @RequestBody SaveClassroomRequest req) {
        return service.update(CurrentUser.of(auth), id, req);
    }

    @Operation(summary = "Delete a classroom", description = "Removes memberships and assignments; quizzes and attempts are kept.")
    @DeleteMapping("/classrooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        service.delete(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Generate a new join code")
    @PostMapping("/classrooms/{id}/join-code")
    public ClassroomDetail regenerateCode(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return service.regenerateCode(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Remove a learner from the classroom")
    @DeleteMapping("/classrooms/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@Parameter(hidden = true) Authentication auth, @PathVariable Long id, @PathVariable Long userId) {
        service.removeMember(CurrentUser.of(auth), id, userId);
    }

    @Operation(summary = "Assign one of my published quizzes", description = "dueAt is optional (ISO-8601).")
    @PostMapping("/classrooms/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView assign(@Parameter(hidden = true) Authentication auth, @PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return service.assign(CurrentUser.of(auth), id, req);
    }

    @Operation(summary = "Remove an assignment")
    @DeleteMapping("/classrooms/{id}/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@Parameter(hidden = true) Authentication auth, @PathVariable Long id, @PathVariable Long assignmentId) {
        service.unassign(CurrentUser.of(auth), id, assignmentId);
    }

    @Operation(summary = "Gradebook: learners × assignments (best score, attempts, progress)")
    @GetMapping("/classrooms/{id}/gradebook")
    public Gradebook gradebook(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        return service.gradebook(CurrentUser.of(auth), id);
    }

    @Operation(summary = "Gradebook as CSV")
    @GetMapping(value = "/classrooms/{id}/gradebook.csv", produces = "text/csv")
    public ResponseEntity<byte[]> gradebookCsv(@Parameter(hidden = true) Authentication auth, @PathVariable Long id) {
        byte[] body = ("﻿" + service.gradebookCsv(CurrentUser.of(auth), id)).getBytes(StandardCharsets.UTF_8);   // BOM: Excel opens UTF-8 correctly
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("gradebook-" + id + ".csv").build().toString())
                .body(body);
    }
}

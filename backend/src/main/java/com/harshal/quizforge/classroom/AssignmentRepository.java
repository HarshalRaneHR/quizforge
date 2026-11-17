package com.harshal.quizforge.classroom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /** Every assignment in every classroom the learner belongs to (quiz + classroom fetched in the same query). */
    @Query("""
            select a from Assignment a
            join fetch a.quiz q join fetch a.classroom c
            where c.id in (select m.classroom.id from ClassroomMember m where m.user.email = :email)
            order by a.dueAt asc nulls last, a.createdAt desc""")
    List<Assignment> findForLearner(@Param("email") String email);
}

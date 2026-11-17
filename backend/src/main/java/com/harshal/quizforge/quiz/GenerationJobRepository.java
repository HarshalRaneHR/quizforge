package com.harshal.quizforge.quiz;

import com.harshal.quizforge.quiz.GenerationJob.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

    boolean existsByUserEmailAndStatusIn(String email, Collection<Status> statuses);

    @Query("select j.user.email from GenerationJob j where j.id = :id")
    String findOwnerEmail(@Param("id") UUID id);

    /** Single UPDATE per progress tick — no read-modify-write race with the polling client. */
    @Transactional
    @Modifying
    @Query("update GenerationJob j set j.doneSteps = :done where j.id = :id")
    void updateProgress(@Param("id") UUID id, @Param("done") int done);

    /** Jobs that were running when the server stopped will never finish: mark them failed on startup. */
    @Transactional
    @Modifying
    @Query("""
            update GenerationJob j set j.status = 'FAILED', j.error = :error, j.finishedAt = :now
            where j.status in ('QUEUED', 'RUNNING')""")
    int failUnfinished(@Param("error") String error, @Param("now") Instant now);
}

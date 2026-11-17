package com.harshal.quizforge.classroom;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    List<Classroom> findByOwnerEmailOrderByCreatedAtDesc(String email);

    @Query("select m.classroom from ClassroomMember m where m.user.email = :email order by m.joinedAt desc")
    List<Classroom> findJoinedBy(@Param("email") String email);

    Optional<Classroom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    @EntityGraph(attributePaths = {"owner"})
    Optional<Classroom> findWithOwnerById(Long id);
}

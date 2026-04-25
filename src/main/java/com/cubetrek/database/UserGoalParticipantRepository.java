package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserGoalParticipantRepository extends JpaRepository<UserGoalParticipant, Long> {
    Optional<UserGoalParticipant> findByGoalAndUser(UserGoal goal, Users user);

    boolean existsByGoalAndUserAndStatus(UserGoal goal, Users user, UserGoalParticipant.ParticipantStatus status);

    @Transactional
    void deleteByGoal(UserGoal goal);

    @Query("""
            SELECT participant.goal
            FROM user_goal_participants participant
            WHERE participant.user = :user
              AND participant.status = :status
            ORDER BY participant.goal.createdTs DESC
            """)
    List<UserGoal> findGoalsByUserAndStatus(@Param("user") Users user,
                                            @Param("status") UserGoalParticipant.ParticipantStatus status);
}

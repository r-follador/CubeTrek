package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserGoalRepository extends JpaRepository<UserGoal, Long> {
    List<UserGoal> findByUser(Users user);
    List<UserGoal> findByUserId(Long userId);

    @Query(value = """
            SELECT CASE
                WHEN :goalMetric = 'DISTANCE' THEN COALESCE(SUM(td.distance), 0) / 1000.0
                WHEN :goalMetric = 'TIME' THEN COALESCE(SUM(td.duration), 0) / 3600.0
                WHEN :goalMetric = 'ELEVATION' THEN COALESCE(SUM(td.elevationup), 0)::double precision
                WHEN :goalMetric = 'ACTIVITIES' THEN COUNT(*)::double precision
                ELSE 0.0
            END
            FROM trackdata td
            WHERE td.owner = :userId
              AND td.activitytype = :activityType
              AND td.hidden = false
              AND td.datetrack >= :startDate
              AND td.datetrack < :endDateExclusive
            """, nativeQuery = true)
    Double calculateFulfillmentValue(@Param("userId") Long userId,
                                     @Param("activityType") int activityType,
                                     @Param("goalMetric") String goalMetric,
                                     @Param("startDate") Instant startDate,
                                     @Param("endDateExclusive") Instant endDateExclusive);

    @Query(value = """
            SELECT td.datetrack AS datetrack,
                   CASE
                       WHEN :goalMetric = 'DISTANCE' THEN td.distance / 1000.0
                       WHEN :goalMetric = 'TIME' THEN td.duration / 3600.0
                       WHEN :goalMetric = 'ELEVATION' THEN COALESCE(td.elevationup, 0)::double precision
                       WHEN :goalMetric = 'ACTIVITIES' THEN 1.0
                       ELSE 0.0
                   END AS value
            FROM trackdata td
            WHERE td.owner = :userId
              AND td.activitytype = :activityType
              AND td.hidden = false
              AND td.datetrack >= :startDate
              AND td.datetrack < :endDateExclusive
            ORDER BY td.datetrack
            """, nativeQuery = true)
    List<GoalProgressPoint> findProgressPoints(@Param("userId") Long userId,
                                               @Param("activityType") int activityType,
                                               @Param("goalMetric") String goalMetric,
                                               @Param("startDate") Instant startDate,
                                               @Param("endDateExclusive") Instant endDateExclusive);

    interface GoalProgressPoint {
        Instant getDatetrack();
        Double getValue();
    }
}

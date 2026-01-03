package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.time.Instant;

@Getter
@Setter
@Entity(name = "user_goals")
@Table(name = "user_goals")
public class UserGoal {

    public enum GoalMetric {
        DISTANCE,
        TIME,
        ELEVATION,
        ACTIVITIES
    }

    public enum PeriodType {
        WEEKLY,
        MONTHLY,
        YEARLY,
        CUSTOM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "activity_type", nullable = false)
    private TrackData.Activitytype activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_metric", nullable = false, length = 16)
    private GoalMetric goalMetric;

    @Column(name = "target_value", nullable = false)
    private Integer targetValue;

    /**
     * Dynamically calculated fulfillment value matching the target metric.
     * Uses the current goal definition to aggregate the relevant activities.
     */
    @Formula("""
            CASE goal_metric
                WHEN 'DISTANCE' THEN (
                    SELECT COALESCE(SUM(td.distance), 0) / 1000.0
                    FROM trackdata td
                    WHERE td.owner = user_id
                      AND td.activitytype = activity_type
                      AND td.hidden = false
                      AND td.datetrack >= start_date
                      AND (end_date IS NULL OR td.datetrack <= end_date)
                )
                WHEN 'TIME' THEN (
                    SELECT COALESCE(SUM(td.duration), 0) / 3600.0
                    FROM trackdata td
                    WHERE td.owner = user_id
                      AND td.activitytype = activity_type
                      AND td.hidden = false
                      AND td.datetrack >= start_date
                      AND (end_date IS NULL OR td.datetrack <= end_date)
                )
                WHEN 'ELEVATION' THEN (
                    SELECT COALESCE(SUM(td.elevationup), 0)::double precision
                    FROM trackdata td
                    WHERE td.owner = user_id
                      AND td.activitytype = activity_type
                      AND td.hidden = false
                      AND td.datetrack >= start_date
                      AND (end_date IS NULL OR td.datetrack <= end_date)
                )
                WHEN 'ACTIVITIES' THEN (
                    SELECT COUNT(*)::double precision
                    FROM trackdata td
                    WHERE td.owner = user_id
                      AND td.activitytype = activity_type
                      AND td.hidden = false
                      AND td.datetrack >= start_date
                      AND (end_date IS NULL OR td.datetrack <= end_date)
                )
                ELSE 0.0
            END
            """)
    private Double actualFulfillmentValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 16)
    private PeriodType periodType;

    @Column(name = "start_date", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant startDate;

    @Column(name = "end_date", columnDefinition = "TIMESTAMPTZ")
    private Instant endDate;

    @Column(name = "week_start_day")
    private Short weekStartDay;

    @Column(name = "created_ts", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdTs;

    @PrePersist
    protected void onCreate() {
        if (createdTs == null) {
            createdTs = Instant.now();
        }
    }
}

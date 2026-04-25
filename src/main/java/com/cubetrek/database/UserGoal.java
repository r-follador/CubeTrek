package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "activity_type", nullable = false)
    private TrackData.Activitytype activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_metric", nullable = false, length = 16)
    private GoalMetric goalMetric;

    @Column(name = "target_value", nullable = false)
    private Integer targetValue;

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

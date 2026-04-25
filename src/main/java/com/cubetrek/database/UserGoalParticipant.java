package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity(name = "user_goal_participants")
@Table(
        name = "user_goal_participants",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_goal_participant", columnNames = {"goal_id", "user_id"})
)
public class UserGoalParticipant {

    public enum ParticipantRole {
        OWNER,
        PARTICIPANT
    }

    public enum ParticipantStatus {
        INVITED,
        JOINED,
        LEFT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private UserGoal goal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private ParticipantRole role = ParticipantRole.PARTICIPANT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ParticipantStatus status = ParticipantStatus.JOINED;

    @Column(name = "joined_ts", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant joinedTs;

    @PrePersist
    protected void onCreate() {
        if (joinedTs == null) {
            joinedTs = Instant.now();
        }
    }
}

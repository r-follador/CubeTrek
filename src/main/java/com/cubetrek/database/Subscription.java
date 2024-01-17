package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(targetEntity = Users.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private Users user;

    @Column
    private String stripeCustomerId;

    @Column
    private String stripeSubscriptionId;

    @Column
    private long current_period_start;

    @Column
    private long current_period_end;

    @Column
    private boolean active;
}

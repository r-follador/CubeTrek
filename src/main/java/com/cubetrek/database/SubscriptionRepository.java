package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Subscription findByStripeCustomerId(String StripeCustomerId);

    Subscription findByStripeSubscriptionId(String StripeSubscriptionId);

    Subscription findByUser(Users user);

    boolean existsByUser(Users users);

    void deleteByUser(Users users);

}

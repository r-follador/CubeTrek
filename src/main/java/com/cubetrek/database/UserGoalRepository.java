package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserGoalRepository extends JpaRepository<UserGoal, Long> {
    List<UserGoal> findByUser(Users user);
    List<UserGoal> findByUserId(Long userId);
}

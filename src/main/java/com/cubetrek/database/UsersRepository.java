package com.cubetrek.database;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long>, JpaSpecificationExecutor<Users> {
    Optional<Users> findByName(String name);
    Optional<Users> findByEmail(String email);

    @Transactional
    @Modifying
    @Query("update users u set u.userTier = :usertier where u.id = :id")
    void updateUserTier(@Param(value = "id") long id, @Param(value="usertier") Users.UserTier usertier);

}
package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long>, JpaSpecificationExecutor<Users> {
    Optional<Users> findByName(String name);
    Optional<Users> findByEmail(String email);
}
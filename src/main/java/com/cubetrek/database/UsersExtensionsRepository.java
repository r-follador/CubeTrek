package com.cubetrek.database;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UsersExtensionsRepository extends JpaRepository<UsersExtensions, Long> {

    @Cacheable("userextendeddata")
    Optional<UsersExtensions> findById(Long id);
}
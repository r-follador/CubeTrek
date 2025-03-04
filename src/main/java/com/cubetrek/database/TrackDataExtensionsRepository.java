package com.cubetrek.database;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TrackDataExtensionsRepository extends JpaRepository<TrackDataExtensions, Long>, JpaSpecificationExecutor<TrackGeodata> {

    @Cacheable("trackextendeddata")
    Optional<TrackDataExtensions> findById(Long id);
}
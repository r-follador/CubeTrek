package com.cubetrek.database;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TrackGeodataRepository extends JpaRepository<TrackGeodata, Long>, JpaSpecificationExecutor<TrackGeodata> {

    @Cacheable("trackgeodata")
    Optional<TrackGeodata> findById(Long id);
}
package com.cubetrek.database;

import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface TrackMetadataRepository extends JpaRepository<TrackMetadata, Long>, JpaSpecificationExecutor<TrackMetadata> {
    Optional<TrackMetadata> findById(Long id);
    List<TrackMetadata> findByOwner(Users owner);

    //find duplicates by key features
    boolean existsByOwnerAndDateTrackAndCenterAndDistanceAndDuration(Users owner, ZonedDateTime dateTrack, Point center, int distance, int duration);

    @Modifying
    @Query("update trackmetadata u set u.title = :title, u.comment = :comment where u.id = :id")
    void updateTrackMetadata(@Param(value = "id") long id, @Param(value = "title") String title, @Param(value = "comment") String comment);

}
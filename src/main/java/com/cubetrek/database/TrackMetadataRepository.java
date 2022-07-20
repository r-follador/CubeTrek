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

    List<TrackMetadata> findByOwnerAndDateTrackAndCenterAndDistanceAndDuration(Users owner, ZonedDateTime dateTrack, Point center, int distance, int duration);


    @Query("select u.owner.id from trackmetadata u where u.id = :id")
    long getOwnerId(@Param(value="id") long id);

    @Modifying
    @Query("update trackmetadata u set u.title = :title, u.comment = :comment, u.activitytype = :activitytype where u.id = :id")
    void updateTrackMetadata(@Param(value = "id") long id, @Param(value = "title") String title, @Param(value = "comment") String comment, @Param(value="activitytype") TrackMetadata.Activitytype activitytype);

    @Modifying
    @Query("update trackmetadata u set u.favorite = :favorite where u.id = :id")
    void updateTrackFavorite(@Param(value = "id") long id, @Param(value = "favorite") boolean favorite);

    @Modifying
    @Query("update trackmetadata u set u.hidden = :hidden where u.id = :id")
    void updateTrackHidden(@Param(value = "id") long id, @Param(value = "hidden") boolean hidden);

    @Modifying
    @Query("update trackmetadata u set u.sharing = :sharing where u.id = :id")
    void updateTrackSharing(@Param(value = "id") long id, @Param(value = "sharing") TrackMetadata.Sharing sharing);

}
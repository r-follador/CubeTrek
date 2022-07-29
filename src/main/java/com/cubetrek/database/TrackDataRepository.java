package com.cubetrek.database;

import org.locationtech.jts.geom.Point;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@CacheConfig(cacheNames={"trackdata"})
public interface TrackDataRepository extends JpaRepository<TrackData, Long>, JpaSpecificationExecutor<TrackData> {

    @Override
    @CacheEvict(value = "trackdata", key = "#id")
    void deleteById(Long id);

    @Cacheable(value = "trackdata", key = "#id")
    Optional<TrackData> findById(Long id);

    Optional<TrackData.TrackMetadata> findTrackMetadataById(Long id);

    List<TrackData> findByOwner(Users owner);

    List<TrackData.TrackMetadata> findMetadataByOwner(Users owner);

    //find duplicates by key features
    boolean existsByOwnerAndDateTrackAndCenterAndDistanceAndDuration(Users owner, ZonedDateTime dateTrack, Point center, int distance, int duration);
    List<TrackData.TrackMetadata> findMetadataByOwnerAndDateTrackAndCenterAndDistanceAndDuration(Users owner, ZonedDateTime dateTrack, Point center, int distance, int duration);

    @Cacheable(cacheNames = "ownerid")
    @Query("select u.owner.id from trackdata u where u.id = :id")
    long getOwnerId(@Param(value="id") long id);

    @Modifying
    @CacheEvict(value = "trackdata", key = "#id")
    @Query("update trackdata u set u.title = :title, u.comment = :comment, u.activitytype = :activitytype where u.id = :id")
    void updateTrackMetadata(@Param(value = "id") long id, @Param(value = "title") String title, @Param(value = "comment") String comment, @Param(value="activitytype") TrackData.Activitytype activitytype);

    @Modifying
    @CacheEvict(value = "trackdata", key = "#id")
    @Query("update trackdata u set u.favorite = :favorite where u.id = :id")
    void updateTrackFavorite(@Param(value = "id") long id, @Param(value = "favorite") boolean favorite);

    @Modifying
    @CacheEvict(value = "trackdata", key = "#id")
    @Query("update trackdata u set u.hidden = :hidden where u.id = :id")
    void updateTrackHidden(@Param(value = "id") long id, @Param(value = "hidden") boolean hidden);

    @Modifying
    @CacheEvict(value = "trackdata", key = "#id")
    @Query("update trackdata u set u.sharing = :sharing where u.id = :id")
    void updateTrackSharing(@Param(value = "id") long id, @Param(value = "sharing") TrackData.Sharing sharing);
}
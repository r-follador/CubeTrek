package com.cubetrek.database;

import org.locationtech.jts.geom.Point;
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

public interface TrackDataRepository extends JpaRepository<TrackData, Long>, JpaSpecificationExecutor<TrackData> {
    @Override
    @CacheEvict(cacheNames = "trackdata", beforeInvocation = false, key = "#result.id")
    <S extends TrackData> S save(S s);

    @Override
    @CacheEvict(cacheNames = "trackdata", beforeInvocation = false, key = "#id")
    void deleteById(Long id);

    @Cacheable("trackdata")
    Optional<TrackData> findById(Long id);

    Optional<TrackData.TrackMetadata> findTrackMetadataById(Long id);

    List<TrackData> findByOwner(Users owner);

    List<TrackData.TrackMetadata> findMetadataByOwner(Users owner);

    //find duplicates by key features
    boolean existsByOwnerAndDateTrackAndCenterAndDistanceAndDuration(Users owner, ZonedDateTime dateTrack, Point center, int distance, int duration);
    List<TrackData> findByOwnerAndDateTrackAndCenterAndDistanceAndDuration(Users owner, ZonedDateTime dateTrack, Point center, int distance, int duration);

    @Cacheable("ownerid")
    @Query("select u.owner.id from trackmetadata u where u.id = :id")
    long getOwnerId(@Param(value="id") long id);

    @Modifying
    @CacheEvict(cacheNames = "trackmetadata", key = "#id")
    @Query("update trackmetadata u set u.title = :title, u.comment = :comment, u.activitytype = :activitytype where u.id = :id")
    void updateTrackMetadata(@Param(value = "id") long id, @Param(value = "title") String title, @Param(value = "comment") String comment, @Param(value="activitytype") TrackData.Activitytype activitytype);

    @Modifying
    @CacheEvict(cacheNames = "trackmetadata", key = "#id")
    @Query("update trackmetadata u set u.favorite = :favorite where u.id = :id")
    void updateTrackFavorite(@Param(value = "id") long id, @Param(value = "favorite") boolean favorite);

    @Modifying
    @CacheEvict(cacheNames = "trackmetadata", key = "#id")
    @Query("update trackmetadata u set u.hidden = :hidden where u.id = :id")
    void updateTrackHidden(@Param(value = "id") long id, @Param(value = "hidden") boolean hidden);

    @Modifying
    @CacheEvict(cacheNames = "trackmetadata", key = "#id")
    @Query("update trackmetadata u set u.sharing = :sharing where u.id = :id")
    void updateTrackSharing(@Param(value = "id") long id, @Param(value = "sharing") TrackData.Sharing sharing);
}
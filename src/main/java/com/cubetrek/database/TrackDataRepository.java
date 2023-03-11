package com.cubetrek.database;

import org.locationtech.jts.geom.Point;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface TrackDataRepository extends JpaRepository<TrackData, Long>, JpaSpecificationExecutor<TrackData> {

    @Override
    @CacheEvict(value = "trackdata", key = "#id")
    void deleteById(Long id);


    @Cacheable(value = "trackdata", key = "#id")
    @EntityGraph(attributePaths={"trackgeodata", "owner"})
    Optional<TrackData> findById(Long id);

    Optional<TrackData.TrackMetadata> findTrackMetadataById(Long id);

    List<TrackData> findByOwner(Users owner);

    List<TrackData.TrackMetadata> findMetadataByOwner(Users owner);

    //find duplicates by key features
    boolean existsByOwnerAndDatetrackAndCenterAndDistanceAndDuration(Users owner, Timestamp dateTrack, Point center, int distance, int duration);
    List<TrackData.TrackMetadata> findMetadataByOwnerAndDatetrackAndCenterAndDistanceAndDuration(Users owner, Timestamp dateTrack, Point center, int distance, int duration);

    @Cacheable(cacheNames = "ownerid")
    @Query("select u.owner.id from trackdata u where u.id = :id")
    long getOwnerId(@Param(value="id") long id);

    @Modifying
    @CacheEvict(value = "trackdata", key = "#id")
    @Query("update trackdata u set u.title = :title, u.comment = :comment, u.activitytype = :activitytype where u.id = :id")
    void updateTrackMetadata(@Param(value = "id") long id, @Param(value = "title") String title, @Param(value = "comment") String comment, @Param(value="activitytype") TrackData.Activitytype activitytype);

    @Modifying
    @CacheEvict(value = "trackdata", key = "#id")
    @Query("update trackdata u set u.title = :title where u.id = :id")
    void updateTrackMetadataTitle(@Param(value = "id") long id, @Param(value = "title") String title);


    @Modifying
    @CacheEvict(value = "trackdata", allEntries = true)
    @Query("""
        UPDATE trackdata SET title= :title
        WHERE owner = :ownerid AND trackgroup = :trackgroupid
    """)
    void batchRenameTrackgroup(@Param(value = "trackgroupid") long trackgroupid, @Param(value = "title") String title, @Param(value = "ownerid") Users owner);


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

    @Query(value =
            "SELECT trackdata.activitytype as activitytype, COUNT(*) as count " +
            "FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false " +
            "GROUP BY trackdata.activitytype " +
            "ORDER BY count DESC;", nativeQuery = true)
    List<ActivityCountInterface> getActivityCounts(@Param(value= "user_id") long user_id);

    public interface ActivityCountInterface {
        int getActivitytype();
        int getCount();
    }

    @Query(value = "SELECT CAST(json_agg(t) as TEXT) FROM (" +
            "SELECT date_trunc('day', trackdata.datetrack at time zone 'utc' at time zone :user_timezone) AS trackdata_day, sum(trackdata.distance) as day_dist, sum(trackdata.elevationup) as day_elevationup, COUNT(trackdata) as number " +
            "FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false AND (date_part('year', trackdata.datetrack) = date_part('year', CURRENT_DATE) OR date_part('year', trackdata.datetrack) = date_part('year', CURRENT_DATE) - 1) " +
            "GROUP BY trackdata_day " +
            "ORDER BY trackdata_day) AS t;", nativeQuery = true)
    String getDailyAggregatedStatsAsJSON(@Param(value= "user_id") long user_id, String user_timezone); //Last two years distance, ascent, number of activities for Heatmap

    @Query(value = "SELECT CAST(json_agg(t) as TEXT) FROM (" +
            "SELECT date_trunc('month', trackdata.datetrack at time zone 'utc' at time zone :user_timezone) AS trackdata_month, trackdata.activitytype, sum(trackdata.distance) as monthly_dist, sum(trackdata.elevationup) as monthly_elevationup " +
            "FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false AND (date_part('year', trackdata.datetrack) = date_part('year', CURRENT_DATE) OR date_part('year', trackdata.datetrack) = date_part('year', CURRENT_DATE) - 1 OR date_part('year', trackdata.datetrack) = date_part('year', CURRENT_DATE) - 2) " +
            "GROUP BY trackdata_month, trackdata.activitytype " +
            "ORDER BY trackdata_month) AS t;", nativeQuery = true)
    String getMonthlyAggregatedStatsAsJSON(@Param(value= "user_id") long user_id, String user_timezone); //Last two years distance, ascent, number of activities for Heatmap

    @Query(value = "SELECT CAST(json_agg(t) as TEXT) FROM (" +
            "SELECT date_trunc('year', trackdata.datetrack at time zone 'utc' at time zone :user_timezone) AS trackdata_year, trackdata.activitytype, sum(trackdata.distance) as yearly_dist, sum(trackdata.elevationup) as yearly_elevationup " +
            "FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false " +
            "GROUP BY trackdata_year, trackdata.activitytype " +
            "ORDER BY trackdata_year) AS t;", nativeQuery = true)
    String getYearlyAggregatedStatsAsJSON(@Param(value= "user_id") long user_id, String user_timezone); //All time distance, ascent, number of activities


    @Query(value = """ 
            SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.distance, trackdata.elevationup, trackdata.hidden, trackdata.duration, trackdata.favorite, trackdata.sharing, trackdata.trackgroup, trackdata.elevationdown, trackdata.highestpoint, trackdata.lowestpoint FROM trackdata
            WHERE trackdata.owner = :user_id AND trackdata.hidden = false AND date_trunc('day', trackdata.datetrack at time zone 'utc' at time zone :user_timezone) = to_timestamp(:day, 'YYYY-MM-DD')
            ORDER BY trackdata.datetrack;
            """, nativeQuery = true)
    List<TrackData.TrackMetadata> findTrackOfGivenDay(long user_id, String day, String user_timezone);

    @Query(value = """ 
            SELECT DISTINCT ON (COALESCE(trackdata.trackgroup, -trackdata.id)) trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.favorite, trackdata.trackgroup, ST_X(trackdata.center) AS longitude, ST_Y(trackdata.center) AS latitude FROM trackdata
            WHERE trackdata.owner = :user_id AND trackdata.hidden = false
            ORDER BY (COALESCE(trackdata.trackgroup, -trackdata.id)), trackdata.datetrack DESC;
            """, nativeQuery = true)
    List<TrackData.TrackMapMetadata> findAllTracksPositionByUser(long user_id);


    public interface PublicActivity {
        TrackData.Activitytype getActivitytype();
        int getId();
        String getTitle();
        String getName();
    }

    @Cacheable(value = "publictracks", key = "#size") //See CubetrekScheduler.emptypublictracksCache for cache clearing every 10min
    @Query(value = """ 
            SELECT trackdata.activitytype, trackdata.id, trackdata.title, users.name FROM trackdata
           JOIN users ON trackdata.owner = users.id
           WHERE trackdata.hidden = false AND trackdata.sharing = 2
           ORDER BY trackdata.upload_date DESC
           LIMIT :size
            """, nativeQuery = true)
    List<PublicActivity> findPublicActivities(int size);


    @Query(value = "SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.distance FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false AND trackdata.datetrack > CURRENT_DATE - INTERVAL '3 months' " +
            "ORDER BY trackdata.distance DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<TrackData.TrackMetadata> findTopDistanceLast3Month(long user_id, int limit);

    @Query(value = "SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.elevationup FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false AND trackdata.datetrack > CURRENT_DATE - INTERVAL '3 months' " +
            "ORDER BY trackdata.elevationup DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<TrackData.TrackMetadata> findTopAscentLast3Month(long user_id, int limit);

    @Query(value = "SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.highestpoint FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false AND trackdata.datetrack > CURRENT_DATE - INTERVAL '3 months' " +
            "ORDER BY trackdata.highestpoint DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<TrackData.TrackMetadata> findTopPeakLast3Month(long user_id, int limit);

    @Query(value = "SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.distance FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false " +
            "ORDER BY trackdata.distance DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<TrackData.TrackMetadata> findTopDistanceAlltime(long user_id, int limit);

    @Query(value = "SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.elevationup FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false " +
            "ORDER BY trackdata.elevationup DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<TrackData.TrackMetadata> findTopAscentAlltime(long user_id, int limit);

    @Query(value = "SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.highestpoint FROM trackdata " +
            "WHERE trackdata.owner = :user_id AND trackdata.hidden = false " +
            "ORDER BY trackdata.highestpoint DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<TrackData.TrackMetadata> findTopPeakAlltime(long user_id, int limit);

    Page<TrackData.TrackMetadata> findByOwnerAndHidden(Users owner, boolean hidden, Pageable pageable);

    List<TrackData.TrackMetadata> findByOwnerAndTrackgroupAndHiddenOrderByDatetrackDesc(Users owner, Long trackgroup, boolean hidden);

    List<TrackData.TrackMetadata> findByOwnerAndHiddenAndActivitytype(Users owner, boolean hidden, TrackData.Activitytype activitytype, Pageable pageable);

    @Query(value = """ 
            SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.distance, trackdata.elevationup, trackdata.hidden, trackdata.duration, trackdata.favorite, trackdata.sharing, trackdata.trackgroup, trackdata.elevationdown, trackdata.highestpoint, trackdata.lowestpoint FROM trackdata
            WHERE trackdata.owner = :user_id AND trackdata.hidden = false
            ORDER BY trackdata.datetrack DESC LIMIT :limit ;
            """, nativeQuery = true)
    List<TrackData.TrackMetadata> findMostRecent(long user_id, int limit);

    @Query(value = """ 
            SELECT trackdata.activitytype, trackdata.id, trackdata.title, trackdata.datetrack, trackdata.distance, trackdata.elevationup, trackdata.hidden, trackdata.duration, trackdata.favorite, trackdata.sharing, trackdata.trackgroup, trackdata.elevationdown, trackdata.highestpoint, trackdata.lowestpoint FROM trackdata
            WHERE trackdata.owner = :user_id AND trackdata.hidden = false AND trackdata.favorite = true
            ORDER BY trackdata.datetrack DESC ;
            """, nativeQuery = true)
    List<TrackData.TrackMetadata> findFavorite(long user_id);

    @Query(value = """ 
            SELECT COUNT(*) FROM trackdata
            WHERE trackdata.owner = :user_id AND trackdata.hidden = false;
            """, nativeQuery = true)
    int countTotalActivities(long user_id);

    long countByOwnerAndHidden(Users users, boolean hidden);

    @Query(value = "SELECT trackdata.* " +
            "FROM trackdata JOIN trackgeodata ON trackdata.trackgeodata_id = trackgeodata.id " +
            "WHERE trackdata.owner = :user_id " +
            "  AND st_DWITHIN(trackgeodata.multilinestring, " +
            "    (SELECT trackgeodata.multilinestring FROM trackdata JOIN trackgeodata ON trackdata.trackgeodata_id = trackgeodata.id " +
            "       WHERE trackdata.id = :trackid), 0.0001) " +
            "  AND st_hausdorffdistance(trackgeodata.multilinestring, " +
            "    (SELECT trackgeodata.multilinestring FROM trackdata JOIN trackgeodata ON trackdata.trackgeodata_id = trackgeodata.id" +
            "        WHERE trackdata.id = :trackid), 1) < 0.005 ", nativeQuery = true)
    List<TrackData> findMatchingActivities(long user_id, long trackid);
}
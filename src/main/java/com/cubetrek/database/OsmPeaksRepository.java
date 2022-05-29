package com.cubetrek.database;

import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OsmPeaksRepository extends JpaRepository<OsmPeaks, Long>, JpaSpecificationExecutor<OsmPeaks> {
    List<OsmPeaks> findByName(String name);

    @Query(value = "SELECT * FROM osm_peaks WHERE (name != '') ORDER BY ST_Distance(geometry,  :geom ) LIMIT :limit", nativeQuery = true)
    List<OsmPeaks> findNearestPeaks(final Point geom, final int limit);

    @Query(value = "SELECT * FROM osm_peaks WHERE geometry && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)", nativeQuery = true)
    List<OsmPeaks> findPeaksWithinBBox(final double minLon, final double minLat, final double maxLon, final double maxLat);


}
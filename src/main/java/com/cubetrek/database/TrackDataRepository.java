package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TrackDataRepository extends JpaRepository<TrackData, Long>, JpaSpecificationExecutor<TrackData> {

}
package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TrackRawfileRepository extends JpaRepository<TrackRawfile, Long>, JpaSpecificationExecutor<TrackRawfile> {

}
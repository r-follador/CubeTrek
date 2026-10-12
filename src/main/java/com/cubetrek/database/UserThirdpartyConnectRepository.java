package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserThirdpartyConnectRepository extends JpaRepository<UserThirdpartyConnect, Long> {

    UserThirdpartyConnect findByGarminUseraccesstoken(String GarminUseraccesstoken);

    UserThirdpartyConnect findByGarminUserid(String garminUserid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserThirdpartyConnect c where c.id = :id")
    Optional<UserThirdpartyConnect> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserThirdpartyConnect c where c.user.id = :userId")
    UserThirdpartyConnect lockByUserId(@Param("userId") Long userId);

    @Query("""
            select c.id from UserThirdpartyConnect c
            where c.garminUseraccesstoken is not null and c.garminUseraccesstoken <> ''
              and c.garminOauth2MigrationCompletedAt is null
            order by c.id
            """)
    List<Long> findGarminMigrationCandidates();

    @Query("""
            select c.id from UserThirdpartyConnect c
            where c.garminOauth2RefreshToken is not null and c.garminOauth2RefreshToken <> ''
              and c.garminOauth2ReconnectRequired = false
              and (c.garminOauth2RefreshTokenExpiresAt is null or c.garminOauth2RefreshTokenExpiresAt <= :threshold)
            order by c.id
            """)
    List<Long> findGarminRefreshCandidates(@Param("threshold") Instant threshold);

    UserThirdpartyConnect findByPolarUserid(String PolarUserid);

    UserThirdpartyConnect findByCorosUserid(String CorosUserid);

    UserThirdpartyConnect findBySuuntoUserid(String suuntoUserid);

    UserThirdpartyConnect findByUser(Users user);

    List<UserThirdpartyConnect> findByCorosUseridIsNotNull();

    void deleteByUser(Users users);

}

package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserThirdpartyConnectRepository extends JpaRepository<UserThirdpartyConnect, Long> {

    UserThirdpartyConnect findByGarminUseraccesstoken(String GarminUseraccesstoken);

    UserThirdpartyConnect findByPolarUserid(String PolarUserid);

    UserThirdpartyConnect findByCorosUserid(String CorosUserid);

    UserThirdpartyConnect findBySuuntoUserid(String suuntoUserid);

    UserThirdpartyConnect findByUser(Users user);

    List<UserThirdpartyConnect> findByCorosUseridIsNotNull();

    void deleteByUser(Users users);

}

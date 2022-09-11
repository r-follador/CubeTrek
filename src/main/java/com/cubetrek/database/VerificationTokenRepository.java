package com.cubetrek.database;

import org.apache.catalina.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    VerificationToken findByToken(String token);
    VerificationToken findByUser(Users user);

    void deleteByUser(Users users);

}

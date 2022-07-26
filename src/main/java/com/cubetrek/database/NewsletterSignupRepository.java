package com.cubetrek.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NewsletterSignupRepository extends JpaRepository<NewsletterSignup, Long>, JpaSpecificationExecutor<NewsletterSignup> {

}
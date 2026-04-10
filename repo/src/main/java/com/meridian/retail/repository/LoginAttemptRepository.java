package com.meridian.retail.repository;

import com.meridian.retail.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByUsernameAndSuccessFalseAndAttemptedAtAfter(String username, LocalDateTime after);

    long countByIpAddressAndSuccessFalseAndAttemptedAtAfter(String ipAddress, LocalDateTime after);

    @Modifying
    @Transactional
    @Query("delete from LoginAttempt l where l.username = :username")
    void deleteByUsername(@Param("username") String username);
}

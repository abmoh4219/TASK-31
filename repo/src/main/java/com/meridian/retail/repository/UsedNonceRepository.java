package com.meridian.retail.repository;

import com.meridian.retail.entity.UsedNonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface UsedNonceRepository extends JpaRepository<UsedNonce, Long> {
    boolean existsByNonce(String nonce);

    @Modifying
    @Transactional
    @Query("delete from UsedNonce n where n.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

package com.meridian.retail.repository;

import com.meridian.retail.entity.TempDownloadLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TempDownloadLinkRepository extends JpaRepository<TempDownloadLink, Long> {
    Optional<TempDownloadLink> findByToken(String token);

    @Modifying
    @Transactional
    @Query("delete from TempDownloadLink l where l.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

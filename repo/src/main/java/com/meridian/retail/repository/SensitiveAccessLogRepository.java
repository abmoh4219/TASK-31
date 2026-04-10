package com.meridian.retail.repository;

import com.meridian.retail.entity.SensitiveAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensitiveAccessLogRepository extends JpaRepository<SensitiveAccessLog, Long> {
    Page<SensitiveAccessLog> findAllByOrderByAccessedAtDesc(Pageable pageable);
}

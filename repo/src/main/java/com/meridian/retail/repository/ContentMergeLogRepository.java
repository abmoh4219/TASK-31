package com.meridian.retail.repository;

import com.meridian.retail.entity.ContentMergeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentMergeLogRepository extends JpaRepository<ContentMergeLog, Long> {
    List<ContentMergeLog> findByMasterIdOrderByMergedAtDesc(Long masterId);
}

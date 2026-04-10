package com.meridian.retail.repository;

import com.meridian.retail.entity.BackupRecord;
import com.meridian.retail.entity.BackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {
    List<BackupRecord> findAllByOrderByCreatedAtDesc();
    List<BackupRecord> findByStatusAndCreatedAtBefore(BackupStatus status, LocalDateTime before);
    Optional<BackupRecord> findTop1ByStatusOrderByCreatedAtDesc(BackupStatus status);
}

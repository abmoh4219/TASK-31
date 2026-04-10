package com.meridian.retail.repository;

import com.meridian.retail.entity.ExportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExportLogRepository extends JpaRepository<ExportLog, Long> {
    List<ExportLog> findTop20ByOrderByExportedAtDesc();
}

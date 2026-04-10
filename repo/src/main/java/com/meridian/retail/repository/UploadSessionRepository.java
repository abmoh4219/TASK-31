package com.meridian.retail.repository;

import com.meridian.retail.entity.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {
    Optional<UploadSession> findByUploadId(String uploadId);
}

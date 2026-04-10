package com.meridian.retail.repository;

import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {
    List<ContentItem> findBySha256Fingerprint(String sha256);
    List<ContentItem> findByStatus(ContentStatus status);
    List<ContentItem> findByMasterId(Long masterId);
    long countByStatus(ContentStatus status);
}

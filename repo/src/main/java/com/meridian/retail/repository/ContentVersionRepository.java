package com.meridian.retail.repository;

import com.meridian.retail.entity.ContentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {
    List<ContentVersion> findByContentIdOrderByVersionNumDesc(Long contentId);
    int countByContentId(Long contentId);
}

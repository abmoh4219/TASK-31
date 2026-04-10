package com.meridian.retail.repository;

import com.meridian.retail.entity.CampaignAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignAttachmentRepository extends JpaRepository<CampaignAttachment, Long> {
    List<CampaignAttachment> findByCampaignIdOrderByVersionDesc(Long campaignId);
    List<CampaignAttachment> findBySha256Checksum(String sha256);
    List<CampaignAttachment> findByOriginalFilenameAndCampaignIdOrderByVersionDesc(String originalFilename, Long campaignId);
}

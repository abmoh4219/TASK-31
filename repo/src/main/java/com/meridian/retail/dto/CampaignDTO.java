package com.meridian.retail.dto;

import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.entity.CampaignType;
import com.meridian.retail.entity.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDTO {
    private Long id;
    private String name;
    private String description;
    private CampaignType type;
    private CampaignStatus status;
    private String receiptWording;
    private String storeId;
    private RiskLevel riskLevel;
    private String createdBy;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;

    public static CampaignDTO from(Campaign c) {
        return CampaignDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .type(c.getType())
                .status(c.getStatus())
                .receiptWording(c.getReceiptWording())
                .storeId(c.getStoreId())
                .riskLevel(c.getRiskLevel())
                .createdBy(c.getCreatedBy())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .createdAt(c.getCreatedAt())
                .build();
    }
}

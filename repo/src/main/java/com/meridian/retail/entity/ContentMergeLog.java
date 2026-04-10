package com.meridian.retail.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_merge_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentMergeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "master_id", nullable = false)
    private Long masterId;

    @Column(name = "merged_ids", nullable = false, columnDefinition = "JSON")
    private String mergedIds;

    @Column(name = "before_json", columnDefinition = "LONGTEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "LONGTEXT")
    private String afterJson;

    @Column(name = "merged_by", nullable = false, length = 100)
    private String mergedBy;

    @Column(name = "merged_at", nullable = false, updatable = false)
    private LocalDateTime mergedAt;

    @PrePersist
    void onCreate() {
        if (mergedAt == null) mergedAt = LocalDateTime.now();
    }
}

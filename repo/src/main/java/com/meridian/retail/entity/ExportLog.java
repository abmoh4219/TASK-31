package com.meridian.retail.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "export_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exported_by", nullable = false, length = 100)
    private String exportedBy;

    @Column(name = "export_type", nullable = false, length = 100)
    private String exportType;

    @Column(name = "filters_applied", columnDefinition = "JSON")
    private String filtersApplied;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "exported_at", nullable = false, updatable = false)
    private LocalDateTime exportedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @PrePersist
    void onCreate() {
        if (exportedAt == null) exportedAt = LocalDateTime.now();
    }
}

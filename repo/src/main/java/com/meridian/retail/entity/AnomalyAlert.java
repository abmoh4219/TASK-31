package com.meridian.retail.entity;

import com.meridian.retail.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "anomaly_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_type", nullable = false, length = 100)
    private String alertType;

    /**
     * Anomaly description can leak attacker behaviour patterns and internal heuristics
     * (which fields tripped which thresholds). Encrypted at rest via
     * {@link EncryptedStringConverter} so a leaked DB dump cannot be mined for the
     * detection logic. Not used in any WHERE/lookup query.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AlertSeverity severity;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @PrePersist
    void onCreate() {
        if (detectedAt == null) detectedAt = LocalDateTime.now();
    }
}

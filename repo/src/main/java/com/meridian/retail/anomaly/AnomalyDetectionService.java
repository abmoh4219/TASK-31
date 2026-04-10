package com.meridian.retail.anomaly;

import com.meridian.retail.entity.AlertSeverity;
import com.meridian.retail.entity.AnomalyAlert;
import com.meridian.retail.repository.AnomalyAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spike-based anomaly detection.
 *
 * Two heuristics for now:
 *   1. Mass deletion:    DELETE events in the last 5 minutes  > 10  -> HIGH alert
 *   2. Repeated exports: EXPORT events in the last 10 minutes > 5   -> MEDIUM alert
 *
 * Each alert is persisted in anomaly_alerts and logged at WARN level so even without
 * the dashboard the admin can spot it in the application log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    public static final String EVT_DELETE = "DELETE";
    public static final String EVT_EXPORT = "EXPORT";

    private static final int MASS_DELETE_THRESHOLD = 10;
    private static final int MASS_DELETE_WINDOW_MIN = 5;
    private static final int EXPORT_THRESHOLD = 5;
    private static final int EXPORT_WINDOW_MIN = 10;

    private final ChangeEventService changeEventService;
    private final AnomalyAlertRepository anomalyAlertRepository;

    @Transactional
    public void detectAnomalies() {
        long deletes = changeEventService.getRecentCount(EVT_DELETE, MASS_DELETE_WINDOW_MIN);
        if (deletes > MASS_DELETE_THRESHOLD) {
            createAlert("MASS_DELETION", AlertSeverity.HIGH,
                    deletes + " deletions in the last " + MASS_DELETE_WINDOW_MIN + " minutes");
        }
        long exports = changeEventService.getRecentCount(EVT_EXPORT, EXPORT_WINDOW_MIN);
        if (exports > EXPORT_THRESHOLD) {
            createAlert("REPEATED_EXPORT", AlertSeverity.MEDIUM,
                    exports + " exports in the last " + EXPORT_WINDOW_MIN + " minutes");
        }
    }

    public AnomalyAlert createAlert(String type, AlertSeverity severity, String description) {
        AnomalyAlert alert = AnomalyAlert.builder()
                .alertType(type)
                .severity(severity)
                .description(description)
                .build();
        AnomalyAlert saved = anomalyAlertRepository.save(alert);
        log.warn("ANOMALY [{}] {}: {}", severity, type, description);
        return saved;
    }
}

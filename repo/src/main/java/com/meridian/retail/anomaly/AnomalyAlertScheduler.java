package com.meridian.retail.anomaly;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically scans recent change events for anomaly bursts. */
@Component
@RequiredArgsConstructor
public class AnomalyAlertScheduler {

    private final AnomalyDetectionService anomalyDetectionService;

    /** Runs every 60 seconds. fixedDelay measures from the END of the previous invocation. */
    @Scheduled(fixedDelay = 60_000L)
    public void runDetection() {
        anomalyDetectionService.detectAnomalies();
    }
}

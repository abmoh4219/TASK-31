package com.meridian.retail.service;

import com.meridian.retail.anomaly.AnomalyDetectionService;
import com.meridian.retail.anomaly.ChangeEventService;
import com.meridian.retail.entity.AlertSeverity;
import com.meridian.retail.entity.AnomalyAlert;
import com.meridian.retail.repository.AnomalyAlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock ChangeEventService changeEventService;
    @Mock AnomalyAlertRepository anomalyAlertRepository;
    @InjectMocks AnomalyDetectionService svc;

    @Test
    void massDeletionTriggersHighAlert() {
        when(changeEventService.getRecentCount(eq("DELETE"), anyInt())).thenReturn(15L);
        when(changeEventService.getRecentCount(eq("EXPORT"), anyInt())).thenReturn(0L);
        when(anomalyAlertRepository.save(any(AnomalyAlert.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        svc.detectAnomalies();

        verify(anomalyAlertRepository, times(1)).save(any(AnomalyAlert.class));
    }

    @Test
    void belowThresholdNoAlert() {
        when(changeEventService.getRecentCount(eq("DELETE"), anyInt())).thenReturn(2L);
        when(changeEventService.getRecentCount(eq("EXPORT"), anyInt())).thenReturn(2L);

        svc.detectAnomalies();

        verify(anomalyAlertRepository, never()).save(any(AnomalyAlert.class));
    }

    @Test
    void repeatedExportsTriggersMediumAlert() {
        when(changeEventService.getRecentCount(eq("DELETE"), anyInt())).thenReturn(0L);
        when(changeEventService.getRecentCount(eq("EXPORT"), anyInt())).thenReturn(8L);
        when(anomalyAlertRepository.save(any(AnomalyAlert.class)))
                .thenAnswer(inv -> {
                    AnomalyAlert a = inv.getArgument(0);
                    org.assertj.core.api.Assertions.assertThat(a.getSeverity())
                            .isEqualTo(AlertSeverity.MEDIUM);
                    return a;
                });

        svc.detectAnomalies();

        verify(anomalyAlertRepository, times(1)).save(any(AnomalyAlert.class));
    }
}

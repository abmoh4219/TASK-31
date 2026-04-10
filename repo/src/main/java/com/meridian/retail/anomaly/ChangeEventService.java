package com.meridian.retail.anomaly;

import com.meridian.retail.entity.ChangeEvent;
import com.meridian.retail.repository.ChangeEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Records lightweight "something happened" events used by AnomalyDetectionService to
 * spot bursts (mass deletes, repeated exports). Distinct from AuditLogService which
 * carries before/after diffs — change events are deliberately small and high-volume.
 */
@Service
@RequiredArgsConstructor
public class ChangeEventService {

    private final ChangeEventRepository changeEventRepository;

    public ChangeEvent record(String eventType, String entityType, Long entityId, String username) {
        return changeEventRepository.save(ChangeEvent.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .username(username)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /** Count of events of {@code eventType} in the last {@code windowMinutes}. */
    public long getRecentCount(String eventType, int windowMinutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        return changeEventRepository.countByEventTypeAndOccurredAtAfter(eventType, since);
    }
}

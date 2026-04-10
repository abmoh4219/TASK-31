package com.meridian.retail.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.retail.entity.AuditLog;
import com.meridian.retail.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Immutable audit logger.
 *
 * REQUIRES_NEW propagation: an audit write must NOT roll back if the surrounding
 * business transaction fails — we want to know about attempted operations even
 * when they didn't complete. This intentionally creates a separate physical
 * transaction for every log() call.
 *
 * before/after states are serialized to JSON. Plain entities are accepted; pass
 * `null` for either side when not applicable (e.g. created has no `before`,
 * deleted has no `after`).
 *
 * The repository this service uses (AuditLogRepository) intentionally does not
 * expose any update operation. All access from the rest of the codebase MUST go
 * through this service so the audit trail remains tamper-evident.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action,
                    String entityType,
                    Long entityId,
                    Object before,
                    Object after,
                    String operatorUsername,
                    String ipAddress) {

        AuditLog entry = AuditLog.builder()
                .action(action.name())
                .entityType(entityType)
                .entityId(entityId)
                .operatorUsername(operatorUsername)
                .ipAddress(ipAddress)
                .createdAt(LocalDateTime.now())
                .beforeState(toJson(before))
                .afterState(toJson(after))
                .build();
        auditLogRepository.save(entry);
        log.debug("AUDIT {} {}#{} by {}", action, entityType, entityId, operatorUsername);
    }

    /** Convenience overload for actions that have no entity context. */
    public void log(AuditAction action, String operatorUsername, String ipAddress) {
        log(action, null, null, null, null, operatorUsername, ipAddress);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit payload: {}", e.getMessage());
            return "{\"error\":\"serialization-failed\"}";
        }
    }
}

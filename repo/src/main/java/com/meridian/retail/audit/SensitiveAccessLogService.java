package com.meridian.retail.audit;

import com.meridian.retail.entity.SensitiveAccessLog;
import com.meridian.retail.repository.SensitiveAccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records access to sensitive fields (e.g. employee notes). Visible only to ADMIN
 * via /admin/sensitive-log. Like AuditLogService it writes in REQUIRES_NEW so the
 * access is logged even if the surrounding read transaction is rolled back.
 */
@Service
@RequiredArgsConstructor
public class SensitiveAccessLogService {

    private final SensitiveAccessLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccess(String fieldName, String entityType, Long entityId,
                          String accessorUsername, String ipAddress) {
        repository.save(SensitiveAccessLog.builder()
                .fieldName(fieldName)
                .entityType(entityType)
                .entityId(entityId)
                .accessorUsername(accessorUsername)
                .ipAddress(ipAddress)
                .build());
    }

    public Page<SensitiveAccessLog> recent(Pageable pageable) {
        return repository.findAllByOrderByAccessedAtDesc(pageable);
    }
}

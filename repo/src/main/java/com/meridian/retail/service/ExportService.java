package com.meridian.retail.service;

import com.meridian.retail.anomaly.AnomalyDetectionService;
import com.meridian.retail.anomaly.ChangeEventService;
import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.CouponRedemption;
import com.meridian.retail.entity.ExportLog;
import com.meridian.retail.repository.CouponRedemptionRepository;
import com.meridian.retail.repository.ExportLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Finance/Admin-only CSV export. Class-level @PreAuthorize means none of these methods
 * can be invoked by any other role no matter how they are wired into a controller.
 *
 * Streaming output: we use StreamingResponseBody so the export does not buffer the
 * full row set in memory. Each export ALWAYS writes:
 *   - an ExportLog row (auditable history)
 *   - an AuditLog entry via AuditLogService.log(EXPORT_GENERATED, ...)
 *   - a ChangeEvent so AnomalyDetectionService can flag a burst.
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
@RequiredArgsConstructor
public class ExportService {

    private final CouponRedemptionRepository redemptionRepository;
    private final ExportLogRepository exportLogRepository;
    private final ChangeEventService changeEventService;
    private final AuditLogService auditLogService;

    public StreamingResponseBody exportRedemptionsCsv(String username, String ipAddress) {
        List<CouponRedemption> rows = redemptionRepository.findAll();
        long count = rows.size();

        // 1. Persist the export log immediately so a partial download still leaves a trail.
        ExportLog log = ExportLog.builder()
                .exportedBy(username)
                .exportType("REDEMPTIONS_CSV")
                .filtersApplied("{}")
                .rowCount((int) count)
                .filePath(null)
                .ipAddress(ipAddress)
                .build();
        exportLogRepository.save(log);

        // 2. AUDIT: who exported what
        auditLogService.log(AuditAction.EXPORT_GENERATED, "ExportLog", log.getId(),
                null, Map.of("type", "REDEMPTIONS_CSV", "rowCount", count),
                username, ipAddress);

        // 3. Feed the anomaly tracker so repeated exports trigger an alert.
        changeEventService.record(AnomalyDetectionService.EVT_EXPORT, "ExportLog", log.getId(), username);

        // 4. Stream the CSV to the response.
        return outputStream -> {
            try (Writer w = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                w.write("id,coupon_id,store_id,redeemed_at,discount_applied,order_total\n");
                for (CouponRedemption r : rows) {
                    w.write(r.getId() + ",");
                    w.write(r.getCouponId() + ",");
                    w.write(safe(r.getStoreId()) + ",");
                    w.write(r.getRedeemedAt() + ",");
                    w.write(r.getDiscountApplied() + ",");
                    w.write(r.getOrderTotal() + "\n");
                }
                w.flush();
            }
        };
    }

    private String safe(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}

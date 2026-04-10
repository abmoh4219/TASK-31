package com.meridian.retail.integrity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentMergeLog;
import com.meridian.retail.entity.ContentStatus;
import com.meridian.retail.repository.ContentItemRepository;
import com.meridian.retail.repository.ContentMergeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges duplicate ContentItems into a single master.
 *
 * Steps (transactional):
 *   1. Snapshot every duplicate to ContentVersion BEFORE mutation (audit + reversibility).
 *   2. Set status=MERGED and master_id on each duplicate.
 *   3. Build a before/after JSON diff and save a ContentMergeLog row.
 *   4. AUDIT: emit AuditAction.CONTENT_MERGED.
 *
 * The duplicates retain all their data — they're not deleted, just flagged. A future
 * rollback can flip status back to ACTIVE if needed.
 */
@Service
@RequiredArgsConstructor
public class MergeService {

    private final ContentItemRepository contentItemRepository;
    private final ContentMergeLogRepository mergeLogRepository;
    private final ContentVersionService contentVersionService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ContentMergeLog merge(Long masterId, List<Long> duplicateIds, String username, String ipAddress) {
        if (duplicateIds == null || duplicateIds.isEmpty()) {
            throw new IllegalArgumentException("duplicateIds must not be empty");
        }
        if (duplicateIds.contains(masterId)) {
            throw new IllegalArgumentException("master cannot be in the duplicates list");
        }
        ContentItem master = contentItemRepository.findById(masterId)
                .orElseThrow(() -> new IllegalArgumentException("Master not found: " + masterId));

        Map<String, Object> beforeMap = new LinkedHashMap<>();
        Map<String, Object> afterMap = new LinkedHashMap<>();

        for (Long dupId : duplicateIds) {
            ContentItem dup = contentItemRepository.findById(dupId)
                    .orElseThrow(() -> new IllegalArgumentException("Duplicate not found: " + dupId));

            // 1. Snapshot every duplicate before mutation
            contentVersionService.snapshotCurrent(dup.getId(), username);

            beforeMap.put(String.valueOf(dupId), Map.of(
                    "title", String.valueOf(dup.getTitle()),
                    "status", dup.getStatus().name(),
                    "masterId", dup.getMasterId() == null ? "null" : dup.getMasterId().toString()
            ));

            // 2. Mark as merged
            dup.setStatus(ContentStatus.MERGED);
            dup.setMasterId(master.getId());
            ContentItem savedDup = contentItemRepository.save(dup);

            afterMap.put(String.valueOf(dupId), Map.of(
                    "title", String.valueOf(savedDup.getTitle()),
                    "status", savedDup.getStatus().name(),
                    "masterId", savedDup.getMasterId().toString()
            ));
        }

        // 3. Persist the merge log entry
        ContentMergeLog logEntry = ContentMergeLog.builder()
                .masterId(master.getId())
                .mergedIds(toJson(duplicateIds))
                .beforeJson(toJson(beforeMap))
                .afterJson(toJson(afterMap))
                .mergedBy(username)
                .build();
        ContentMergeLog savedLog = mergeLogRepository.save(logEntry);

        // 4. Audit
        auditLogService.log(AuditAction.CONTENT_MERGED, "ContentItem", master.getId(),
                beforeMap, afterMap, username, ipAddress);

        return savedLog;
    }

    /** Field-by-field diff between two arbitrary objects (used for the UI before/after view). */
    public Map<String, Object[]> computeJsonDiff(Object before, Object after) {
        Map<String, Object> b = objectMapper.convertValue(before, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
        Map<String, Object> a = objectMapper.convertValue(after, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
        Map<String, Object[]> diff = new LinkedHashMap<>();
        for (String key : b.keySet()) {
            Object bv = b.get(key);
            Object av = a.get(key);
            if ((bv == null && av != null) || (bv != null && !bv.equals(av))) {
                diff.put(key, new Object[]{bv, av});
            }
        }
        for (String key : a.keySet()) {
            if (!b.containsKey(key)) diff.put(key, new Object[]{null, a.get(key)});
        }
        return diff;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}

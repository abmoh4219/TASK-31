package com.meridian.retail.integrity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentVersion;
import com.meridian.retail.repository.ContentItemRepository;
import com.meridian.retail.repository.ContentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Captures point-in-time snapshots of a ContentItem and supports rollback.
 *
 * snapshotCurrent: serializes the current state of the content row to JSON and inserts
 * a new ContentVersion with version_num = max(existing) + 1.
 *
 * rollback: snapshots the current state (so the rollback itself is reversible),
 * deserializes the target version's snapshot, applies its fields to the live row,
 * and emits an audit log entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentVersionService {

    private final ContentItemRepository contentItemRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ContentVersion snapshotCurrent(Long contentId, String username) {
        ContentItem current = contentItemRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Content not found: " + contentId));

        int nextVersion = contentVersionRepository.countByContentId(contentId) + 1;
        ContentVersion v = ContentVersion.builder()
                .contentId(contentId)
                .versionNum(nextVersion)
                .snapshotJson(toJson(current))
                .changedBy(username)
                .build();
        return contentVersionRepository.save(v);
    }

    public List<ContentVersion> getHistory(Long contentId) {
        return contentVersionRepository.findByContentIdOrderByVersionNumDesc(contentId);
    }

    @Transactional
    public ContentItem rollback(Long contentId, int targetVersionNum, String username, String ipAddress) {
        ContentItem current = contentItemRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Content not found: " + contentId));

        // 1. Snapshot the current state for the audit / future rollback chain.
        snapshotCurrent(contentId, username);
        Map<String, Object> beforeMap = serializeAsMap(current);

        // 2. Find the target version.
        ContentVersion target = getHistory(contentId).stream()
                .filter(v -> v.getVersionNum() == targetVersionNum)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Version " + targetVersionNum + " not found for content " + contentId));

        // 3. Deserialize the snapshot and apply its mutable fields back onto the live row.
        try {
            Map<String, Object> snapshotMap = objectMapper.readValue(
                    target.getSnapshotJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (snapshotMap.get("title") != null) current.setTitle(String.valueOf(snapshotMap.get("title")));
            if (snapshotMap.get("bodyText") != null) current.setBodyText(String.valueOf(snapshotMap.get("bodyText")));
            if (snapshotMap.get("sourceUrl") != null) current.setSourceUrl(String.valueOf(snapshotMap.get("sourceUrl")));
            if (snapshotMap.get("normalizedUrl") != null) current.setNormalizedUrl(String.valueOf(snapshotMap.get("normalizedUrl")));
            if (snapshotMap.get("sha256Fingerprint") != null) current.setSha256Fingerprint(String.valueOf(snapshotMap.get("sha256Fingerprint")));
            if (snapshotMap.get("simHash") instanceof Number n) current.setSimHash(n.longValue());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize snapshot for version " + targetVersionNum, e);
        }

        ContentItem saved = contentItemRepository.save(current);
        auditLogService.log(AuditAction.CONTENT_ROLLED_BACK, "ContentItem", contentId,
                beforeMap, serializeAsMap(saved), username, ipAddress);
        return saved;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> serializeAsMap(Object o) {
        try {
            return objectMapper.convertValue(o, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}

package com.meridian.retail.integrity;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentStatus;
import com.meridian.retail.repository.ContentItemRepository;
import com.meridian.retail.security.XssInputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentImportService {

    private final ContentItemRepository contentItemRepository;
    private final FingerprintService fingerprintService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final AuditLogService auditLogService;

    public record ImportResult(int imported, int duplicatesFound, List<String> errors) {}

    /**
     * CSV format (header row required):
     *   title,source_url,body_text
     *
     * Each row is normalized, fingerprinted, and stored. If an exact or near duplicate
     * already exists the new row is still saved (with status=ACTIVE) but is counted toward
     * duplicatesFound so the operator can review and merge afterwards.
     */
    @Transactional
    public ImportResult importFromCsv(MultipartFile file, Long campaignId, String username, String ipAddress) {
        int imported = 0;
        int duplicatesFound = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null) {
                return new ImportResult(0, 0, List.of("Empty CSV"));
            }
            int lineNo = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                String[] parts = parseCsvLine(line);
                if (parts.length < 3) {
                    errors.add("Line " + lineNo + ": expected 3 columns, got " + parts.length);
                    continue;
                }
                String title = XssInputSanitizer.sanitize(parts[0]);
                String url = XssInputSanitizer.sanitize(parts[1]);
                String body = XssInputSanitizer.sanitize(parts[2]);

                ContentItem saved = saveOne(campaignId, title, url, body, username);
                imported++;

                // Count exact + near duplicates that already existed PRIOR to this row.
                List<ContentItem> exact = duplicateDetectionService.findExactDuplicates(saved.getSha256Fingerprint());
                if (exact.size() > 1) {
                    duplicatesFound++;
                } else {
                    var near = duplicateDetectionService.findNearDuplicates(saved.getSimHash());
                    if (near.size() > 1) duplicatesFound++;
                }
            }
        } catch (Exception e) {
            errors.add("CSV read error: " + e.getMessage());
        }

        auditLogService.log(AuditAction.CONTENT_IMPORTED, "ContentItem", null,
                null, java.util.Map.of("imported", imported, "duplicates", duplicatesFound),
                username, ipAddress);
        return new ImportResult(imported, duplicatesFound, errors);
    }

    /** Single-item import for the "Add manually" form. */
    @Transactional
    public ContentItem importSingle(Long campaignId, String title, String sourceUrl, String body,
                                    String username, String ipAddress) {
        ContentItem saved = saveOne(campaignId,
                XssInputSanitizer.sanitize(title),
                XssInputSanitizer.sanitize(sourceUrl),
                XssInputSanitizer.sanitize(body),
                username);
        auditLogService.log(AuditAction.CONTENT_IMPORTED, "ContentItem", saved.getId(),
                null, java.util.Map.of("title", title), username, ipAddress);
        return saved;
    }

    private ContentItem saveOne(Long campaignId, String title, String sourceUrl, String body, String username) {
        String normalized = fingerprintService.normalizeUrl(sourceUrl);
        String sha256 = fingerprintService.computeSha256(body == null ? "" : body);
        long simHash = fingerprintService.computeSimHash(body == null ? "" : body);

        ContentItem item = ContentItem.builder()
                .campaignId(campaignId)
                .title(title)
                .sourceUrl(sourceUrl)
                .normalizedUrl(normalized)
                .bodyText(body)
                .sha256Fingerprint(sha256)
                .simHash(simHash)
                .status(ContentStatus.ACTIVE)
                .importedBy(username)
                .build();
        return contentItemRepository.save(item);
    }

    /**
     * Minimal CSV line parser supporting double-quoted fields with embedded commas.
     */
    private String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}

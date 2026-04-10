package com.meridian.retail.storage;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.CampaignAttachment;
import com.meridian.retail.repository.CampaignAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * Masked download policy:
 *
 *   - Public attachment        -> stream original to anyone authenticated
 *   - Internal-only attachment:
 *       - role IN masked_roles -> stream the original
 *       - role NOT in list:
 *           - PDF or image     -> stream watermarked rendition
 *           - other binary     -> 403
 *
 * Every access — original or watermarked — writes an AuditLog row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaskedDownloadService {

    private final CampaignAttachmentRepository attachmentRepository;
    private final WatermarkService watermarkService;
    private final AuditLogService auditLogService;

    private static final Set<String> WATERMARKABLE = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/gif"
    );

    /** Returns true if {@code role} appears in the JSON list stored on the attachment. */
    public boolean canAccessOriginal(CampaignAttachment attachment, String role) {
        if (!attachment.isInternalOnly()) return true;
        String masked = attachment.getMaskedRoles();
        if (masked == null || masked.isBlank()) return false;
        // The masked_roles JSON is a small list of role strings — substring match is fine.
        return masked.contains("\"" + role + "\"");
    }

    /**
     * Streams the file (original or watermarked) to the response output stream.
     * Returns the MIME type that should be set on the response.
     */
    public String serve(Long attachmentId, String username, String role, String ipAddress, OutputStream out) {
        CampaignAttachment file = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new StorageException("Attachment not found: " + attachmentId));
        Path path = Paths.get(file.getStoredPath());

        boolean originalAccess = canAccessOriginal(file, role);

        if (originalAccess) {
            try {
                Files.copy(path, out);
            } catch (Exception e) {
                throw new StorageException("Failed to stream attachment " + attachmentId, e);
            }
            // AUDIT: original download
            auditLogService.log(AuditAction.FILE_DOWNLOADED, "CampaignAttachment", file.getId(),
                    null, Map.of("filename", file.getOriginalFilename(), "watermarked", false),
                    username, ipAddress);
            return file.getFileType();
        }

        // Not authorized for the original — watermarked rendition or 403.
        if (!WATERMARKABLE.contains(file.getFileType())) {
            // AUDIT: blocked masked access (binary file)
            auditLogService.log(AuditAction.FILE_MASKED_ACCESS, "CampaignAttachment", file.getId(),
                    null, Map.of("blocked", true, "reason", "non-watermarkable binary"),
                    username, ipAddress);
            throw new org.springframework.security.access.AccessDeniedException(
                    "This attachment cannot be masked for your role");
        }

        if ("application/pdf".equals(file.getFileType())) {
            watermarkService.addPdfWatermark(path, username, out);
            auditLogService.log(AuditAction.FILE_MASKED_ACCESS, "CampaignAttachment", file.getId(),
                    null, Map.of("watermarked", true, "type", "pdf"),
                    username, ipAddress);
            return "application/pdf";
        } else {
            watermarkService.addImageWatermark(path, file.getFileType(), username, out);
            auditLogService.log(AuditAction.FILE_MASKED_ACCESS, "CampaignAttachment", file.getId(),
                    null, Map.of("watermarked", true, "type", "image"),
                    username, ipAddress);
            return "image/png"; // image watermark always re-encodes to PNG
        }
    }
}

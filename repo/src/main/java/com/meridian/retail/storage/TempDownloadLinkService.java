package com.meridian.retail.storage;

import com.meridian.retail.entity.CampaignAttachment;
import com.meridian.retail.entity.TempDownloadLink;
import com.meridian.retail.repository.CampaignAttachmentRepository;
import com.meridian.retail.repository.TempDownloadLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-user, time-limited download tokens.
 *
 * SPEC.md: temporary download links expire after 10 minutes and are bound to the
 * requesting user. We enforce both rules in resolve() and additionally mark the link
 * as used so the same token cannot be replayed even within the 10-minute window.
 */
@Service
@RequiredArgsConstructor
public class TempDownloadLinkService {

    private final TempDownloadLinkRepository linkRepository;
    private final CampaignAttachmentRepository attachmentRepository;

    @Value("${app.temp-link.expiry-minutes:10}")
    private int expiryMinutes;

    @Transactional
    public TempDownloadLink generate(Long fileId, String username, String ipAddress) {
        CampaignAttachment file = attachmentRepository.findById(fileId)
                .orElseThrow(() -> new StorageException("Attachment not found: " + fileId));

        TempDownloadLink link = TempDownloadLink.builder()
                .token(UUID.randomUUID().toString())
                .fileId(file.getId())
                .username(username)
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                .build();
        return linkRepository.save(link);
    }

    /** Resolved download = (attachment, filesystem path) returned together. */
    public record Resolved(CampaignAttachment attachment, Path path) {}

    /**
     * Resolves a token to its file. Validates expiry, single-use, and user binding.
     * Throws LinkExpiredException, AccessDeniedException, or StorageException as appropriate.
     */
    @Transactional
    public Resolved resolve(String token, String requestingUsername) {
        TempDownloadLink link = linkRepository.findByToken(token)
                .orElseThrow(() -> new StorageException("Unknown download token"));

        if (link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new LinkExpiredException("Download link has expired");
        }
        if (link.getUsedAt() != null) {
            throw new LinkExpiredException("Download link has already been used");
        }
        if (!link.getUsername().equalsIgnoreCase(requestingUsername)) {
            throw new AccessDeniedException("Download link is bound to a different user");
        }

        link.setUsedAt(LocalDateTime.now());
        linkRepository.save(link);

        CampaignAttachment file = attachmentRepository.findById(link.getFileId())
                .orElseThrow(() -> new StorageException("Attachment not found"));
        return new Resolved(file, Paths.get(file.getStoredPath()));
    }
}

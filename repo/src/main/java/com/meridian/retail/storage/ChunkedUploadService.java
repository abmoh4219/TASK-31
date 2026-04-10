package com.meridian.retail.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.AttachmentStatus;
import com.meridian.retail.entity.CampaignAttachment;
import com.meridian.retail.entity.UploadSession;
import com.meridian.retail.repository.CampaignAttachmentRepository;
import com.meridian.retail.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resumable, chunked upload protocol:
 *
 *   POST /files/upload/init       -> ChunkedUploadService.initUpload   (returns uploadId)
 *   POST /files/upload/chunk      -> ChunkedUploadService.receiveChunk (per chunk)
 *   GET  /files/upload/status/{}  -> ChunkedUploadService.getStatus    (HTMX polling)
 *   POST /files/upload/finalize/{}-> ChunkedUploadService.finalizeUpload
 *
 * After receiveChunk the upload session's received_chunks JSON list grows. Once all
 * chunks have been written, finalizeUpload assembles them, validates the MIME via Tika,
 * computes SHA-256, persists a CampaignAttachment row, and writes an audit log entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkedUploadService {

    private final UploadSessionRepository sessionRepository;
    private final CampaignAttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final FileValidationService fileValidationService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public UploadSession initUpload(Long campaignId, String filename, int totalChunks, String username) {
        if (totalChunks <= 0) {
            throw new StorageException("totalChunks must be positive");
        }
        String uploadId = UUID.randomUUID().toString();
        Path tempDir = storageService.getTempDir(uploadId);

        UploadSession session = UploadSession.builder()
                .uploadId(uploadId)
                .campaignId(campaignId)
                .originalFilename(filename)
                .totalChunks(totalChunks)
                .receivedChunks("[]")
                .tempDir(tempDir.toString())
                .status(AttachmentStatus.IN_PROGRESS)
                .startedBy(username)
                .build();
        return sessionRepository.save(session);
    }

    @Transactional
    public UploadSession receiveChunk(String uploadId, int chunkIndex, byte[] bytes) {
        UploadSession session = sessionRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new StorageException("Unknown uploadId: " + uploadId));
        if (session.getStatus() != AttachmentStatus.IN_PROGRESS) {
            throw new StorageException("Upload not in progress: " + session.getStatus());
        }
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new StorageException("Chunk index out of range");
        }
        storageService.storeChunk(uploadId, chunkIndex, bytes);

        List<Integer> received = parseChunkList(session.getReceivedChunks());
        if (!received.contains(chunkIndex)) received.add(chunkIndex);
        session.setReceivedChunks(toJson(received));
        return sessionRepository.save(session);
    }

    public Optional<Map<String, Object>> getStatus(String uploadId) {
        return sessionRepository.findByUploadId(uploadId).map(s -> {
            List<Integer> received = parseChunkList(s.getReceivedChunks());
            int progress = s.getTotalChunks() == 0 ? 0
                    : (int) Math.round(100.0 * received.size() / s.getTotalChunks());
            return Map.of(
                    "uploadId", s.getUploadId(),
                    "totalChunks", s.getTotalChunks(),
                    "receivedChunks", received,
                    "progress", progress,
                    "status", s.getStatus().name()
            );
        });
    }

    /**
     * Backwards-compatible: finalize without custom visibility flags — attachment is
     * stored as public (internalOnly=false, maskedRoles=null).
     */
    @Transactional
    public CampaignAttachment finalizeUpload(String uploadId, String username, String ipAddress) {
        return finalizeUpload(uploadId, username, ipAddress, false, null);
    }

    /**
     * Finalize with visibility controls. internalOnly=true means non-privileged roles
     * will only be served the watermarked rendition (or 403 for non-watermarkable files).
     * maskedRoles is a JSON array string like ["ADMIN","FINANCE"] — roles in the list
     * are served the ORIGINAL; everyone else is masked.
     */
    @Transactional
    public CampaignAttachment finalizeUpload(String uploadId, String username, String ipAddress,
                                             boolean internalOnly, String maskedRolesJson) {
        UploadSession session = sessionRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new StorageException("Unknown uploadId: " + uploadId));
        if (session.getStatus() != AttachmentStatus.IN_PROGRESS) {
            throw new StorageException("Upload already finalized");
        }

        List<Integer> received = parseChunkList(session.getReceivedChunks());
        if (received.size() != session.getTotalChunks()) {
            throw new StorageException("Cannot finalize — missing chunks (" + received.size()
                    + " of " + session.getTotalChunks() + ")");
        }

        // Determine version number for this filename in this campaign
        List<CampaignAttachment> existing = attachmentRepository
                .findByOriginalFilenameAndCampaignIdOrderByVersionDesc(
                        session.getOriginalFilename(), session.getCampaignId());
        int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;

        String storedFilename = "v" + nextVersion + "_" + sanitizeFilename(session.getOriginalFilename());
        Path destPath = storageService.getCampaignDir(session.getCampaignId()).resolve(storedFilename);

        storageService.assembleChunks(uploadId, session.getTotalChunks(), destPath);

        long fileSize;
        try {
            fileSize = Files.size(destPath);
        } catch (Exception e) {
            throw new StorageException("Cannot stat assembled file", e);
        }
        fileValidationService.validateSize(fileSize);

        // MIME validation reads the first few bytes from the assembled file.
        String mime;
        try (var in = Files.newInputStream(destPath)) {
            mime = fileValidationService.detectAndValidateMime(in);
        } catch (Exception e) {
            throw new StorageException("Failed to validate file MIME", e);
        }

        String sha256 = storageService.computeSha256(destPath);

        CampaignAttachment attachment = CampaignAttachment.builder()
                .campaignId(session.getCampaignId())
                .originalFilename(session.getOriginalFilename())
                .storedFilename(storedFilename)
                .storedPath(destPath.toString())
                .fileType(mime)
                .fileSizeBytes(fileSize)
                .sha256Checksum(sha256)
                .internalOnly(internalOnly)
                .maskedRoles(maskedRolesJson)
                .version(nextVersion)
                .uploadedBy(username)
                .build();
        CampaignAttachment saved = attachmentRepository.save(attachment);

        session.setStatus(AttachmentStatus.COMPLETE);
        sessionRepository.save(session);
        storageService.cleanupTemp(uploadId);

        // AUDIT: file uploads must always leave a record (operator + checksum visible).
        auditLogService.log(AuditAction.FILE_UPLOADED, "CampaignAttachment", saved.getId(),
                null, Map.of(
                        "filename", saved.getOriginalFilename(),
                        "version", saved.getVersion(),
                        "sha256", saved.getSha256Checksum(),
                        "size", saved.getFileSizeBytes()
                ),
                username, ipAddress);

        return saved;
    }

    /** Convenience for tests / direct (non-chunked) writes. */
    public CampaignAttachment storeSingleFile(Long campaignId, String filename,
                                              byte[] data, String username, String ipAddress) {
        UploadSession s = initUpload(campaignId, filename, 1, username);
        receiveChunk(s.getUploadId(), 0, data);
        return finalizeUpload(s.getUploadId(), username, ipAddress);
    }

    private List<Integer> parseChunkList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(List<Integer> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

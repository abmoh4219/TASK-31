package com.meridian.retail.controller;

import com.meridian.retail.entity.CampaignAttachment;
import com.meridian.retail.entity.TempDownloadLink;
import com.meridian.retail.entity.UploadSession;
import com.meridian.retail.repository.CampaignAttachmentRepository;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.storage.ChunkedUploadService;
import com.meridian.retail.storage.LinkExpiredException;
import com.meridian.retail.storage.MaskedDownloadService;
import com.meridian.retail.storage.StorageException;
import com.meridian.retail.storage.TempDownloadLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * File upload + download endpoints.
 *
 * Upload protocol:
 *   POST /files/upload/init                {campaignId, filename, totalChunks}
 *   POST /files/upload/chunk               body=chunk bytes; headers Upload-Id, Chunk-Index
 *   GET  /files/upload/status/{uploadId}   JSON {progress, receivedChunks, status}
 *   POST /files/upload/finalize/{uploadId}
 *
 * Download:
 *   GET /files/attachment/{id}/download  -> generates a temp link, redirects to /files/download/{token}
 *   GET /files/download/{token}          -> resolves token, streams file (or watermarked rendition)
 */
@Controller
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final ChunkedUploadService chunkedUploadService;
    private final TempDownloadLinkService tempLinkService;
    private final MaskedDownloadService maskedDownloadService;
    private final CampaignAttachmentRepository attachmentRepository;
    private final CampaignRepository campaignRepository;

    /** Upload landing page (drag-and-drop UI). */
    @GetMapping("/upload")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String uploadPage(@RequestParam(required = false) Long campaignId, Model model) {
        model.addAttribute("breadcrumb", "Upload Files");
        model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
        model.addAttribute("selectedCampaignId", campaignId);
        if (campaignId != null) {
            model.addAttribute("attachments",
                    attachmentRepository.findByCampaignIdOrderByVersionDesc(campaignId));
        } else {
            model.addAttribute("attachments", List.of());
        }
        return "upload/upload";
    }

    @PostMapping(value = "/upload/init", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @ResponseBody
    public Map<String, Object> initUpload(@RequestParam Long campaignId,
                                          @RequestParam String filename,
                                          @RequestParam int totalChunks,
                                          Authentication auth) {
        UploadSession session = chunkedUploadService.initUpload(campaignId, filename, totalChunks, auth.getName());
        return Map.of(
                "uploadId", session.getUploadId(),
                "totalChunks", session.getTotalChunks()
        );
    }

    @PostMapping(value = "/upload/chunk", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @ResponseBody
    public Map<String, Object> uploadChunk(@RequestHeader("Upload-Id") String uploadId,
                                           @RequestHeader("Chunk-Index") int chunkIndex,
                                           @RequestParam("chunk") MultipartFile chunk) throws IOException {
        UploadSession session = chunkedUploadService.receiveChunk(uploadId, chunkIndex, chunk.getBytes());
        var status = chunkedUploadService.getStatus(uploadId).orElseThrow();
        return status;
    }

    @GetMapping(value = "/upload/status/{uploadId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadStatus(@PathVariable String uploadId) {
        return chunkedUploadService.getStatus(uploadId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/upload/finalize/{uploadId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @ResponseBody
    public Map<String, Object> finalizeUpload(@PathVariable String uploadId,
                                              Authentication auth,
                                              HttpServletRequest httpRequest) {
        CampaignAttachment saved = chunkedUploadService.finalizeUpload(uploadId, auth.getName(), clientIp(httpRequest));
        return Map.of(
                "id", saved.getId(),
                "filename", saved.getOriginalFilename(),
                "version", saved.getVersion(),
                "sha256", saved.getSha256Checksum(),
                "size", saved.getFileSizeBytes()
        );
    }

    /** Generates a temp link bound to current user, redirects to it. */
    @GetMapping("/attachment/{id}/download")
    public String requestDownload(@PathVariable Long id,
                                  Authentication auth,
                                  HttpServletRequest httpRequest) {
        TempDownloadLink link = tempLinkService.generate(id, auth.getName(), clientIp(httpRequest));
        return "redirect:/files/download/" + link.getToken();
    }

    @GetMapping("/download/{token}")
    public void download(@PathVariable String token,
                         Authentication auth,
                         HttpServletRequest httpRequest,
                         HttpServletResponse response) throws IOException {
        com.meridian.retail.storage.TempDownloadLinkService.Resolved resolved;
        try {
            // Resolve binds the token to the requesting user and marks it used.
            resolved = tempLinkService.resolve(token, auth.getName());
        } catch (LinkExpiredException e) {
            response.sendError(HttpStatus.GONE.value(), e.getMessage());
            return;
        } catch (AccessDeniedException e) {
            response.sendError(HttpStatus.FORBIDDEN.value(), e.getMessage());
            return;
        }

        CampaignAttachment attachment = resolved.attachment();
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_USER")
                .replace("ROLE_", "");

        try {
            String mime = maskedDownloadService.serve(attachment.getId(), auth.getName(), role,
                    clientIp(httpRequest), response.getOutputStream());
            response.setContentType(mime);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + attachment.getOriginalFilename() + "\"");
        } catch (AccessDeniedException e) {
            response.sendError(HttpStatus.FORBIDDEN.value(), e.getMessage());
        }
    }

    /** Version history page for an attachment. */
    @GetMapping("/attachment/{id}/history")
    public String history(@PathVariable Long id, Model model) {
        CampaignAttachment current = attachmentRepository.findById(id)
                .orElseThrow(() -> new StorageException("Attachment not found: " + id));
        model.addAttribute("breadcrumb", "Attachment History");
        model.addAttribute("current", current);
        model.addAttribute("versions",
                attachmentRepository.findByOriginalFilenameAndCampaignIdOrderByVersionDesc(
                        current.getOriginalFilename(), current.getCampaignId()));
        return "upload/upload"; // reuse upload page which renders the history table
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) return header.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}

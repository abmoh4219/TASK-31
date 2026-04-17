package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.FileController;
import com.meridian.retail.entity.CampaignAttachment;
import com.meridian.retail.entity.TempDownloadLink;
import com.meridian.retail.entity.UploadSession;
import com.meridian.retail.repository.CampaignAttachmentRepository;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.security.CampaignAccessPolicy;
import com.meridian.retail.storage.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileControllerTest {

    @Mock ChunkedUploadService chunkedUploadService;
    @Mock TempDownloadLinkService tempLinkService;
    @Mock MaskedDownloadService maskedDownloadService;
    @Mock CampaignAttachmentRepository attachmentRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock CampaignAccessPolicy campaignAccessPolicy;
    @Mock Model model;
    @Mock Authentication auth;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock MultipartFile chunk;

    FileController controller;

    @BeforeEach
    void setUp() {
        controller = new FileController(chunkedUploadService, tempLinkService,
                maskedDownloadService, attachmentRepository, campaignRepository, campaignAccessPolicy);
        when(auth.getName()).thenReturn("ops");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── uploadPage ────────────────────────────────────────────────────────────

    @Test
    void uploadPageReturnsUploadView() {
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        String view = controller.uploadPage(null, model);
        assertThat(view).isEqualTo("upload/upload");
    }

    @Test
    void uploadPageWithCampaignIdLoadsAttachments() {
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        when(attachmentRepository.findByCampaignIdOrderByVersionDesc(1L)).thenReturn(List.of());
        controller.uploadPage(1L, model);
        verify(attachmentRepository).findByCampaignIdOrderByVersionDesc(1L);
    }

    @Test
    void uploadPageWithoutCampaignIdSetsEmptyAttachments() {
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        controller.uploadPage(null, model);
        verify(model).addAttribute("attachments", List.of());
    }

    // ── initUpload ────────────────────────────────────────────────────────────

    @Test
    void initUploadReturnsUploadIdAndTotalChunks() {
        UploadSession session = mock(UploadSession.class);
        when(session.getUploadId()).thenReturn("upload-uuid-123");
        when(session.getTotalChunks()).thenReturn(3);
        when(chunkedUploadService.initUpload(1L, "test.pdf", 3, "ops")).thenReturn(session);

        Map<String, Object> result = controller.initUpload(1L, "test.pdf", 3, auth);
        assertThat(result).containsEntry("uploadId", "upload-uuid-123");
        assertThat(result).containsEntry("totalChunks", 3);
    }

    // ── uploadChunk ───────────────────────────────────────────────────────────

    @Test
    void uploadChunkCallsChunkedUploadServiceAndReturnsStatus() throws Exception {
        UploadSession session = mock(UploadSession.class);
        when(chunkedUploadService.receiveChunk(anyString(), anyInt(), any())).thenReturn(session);
        Map<String, Object> status = Map.of("progress", 33, "status", "IN_PROGRESS");
        when(chunkedUploadService.getStatus("upload-id")).thenReturn(Optional.of(status));
        when(chunk.getBytes()).thenReturn(new byte[]{1, 2, 3});

        Map<String, Object> result = controller.uploadChunk("upload-id", 0, chunk);
        assertThat(result).containsKey("progress");
    }

    // ── uploadStatus ──────────────────────────────────────────────────────────

    @Test
    void uploadStatusWithFoundIdReturnsOkResponse() {
        Map<String, Object> status = Map.of("status", "IN_PROGRESS", "receivedChunks", 1);
        when(chunkedUploadService.getStatus("test-id")).thenReturn(Optional.of(status));
        ResponseEntity<Map<String, Object>> resp = controller.uploadStatus("test-id");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).containsKey("status");
    }

    @Test
    void uploadStatusWithNotFoundIdReturns404() {
        when(chunkedUploadService.getStatus("unknown")).thenReturn(Optional.empty());
        ResponseEntity<Map<String, Object>> resp = controller.uploadStatus("unknown");
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── finalizeUpload ────────────────────────────────────────────────────────

    @Test
    void finalizeUploadReturnsAttachmentDetails() {
        CampaignAttachment saved = mock(CampaignAttachment.class);
        when(saved.getId()).thenReturn(1L);
        when(saved.getOriginalFilename()).thenReturn("doc.pdf");
        when(saved.getVersion()).thenReturn(1);
        when(saved.getSha256Checksum()).thenReturn("abc123");
        when(saved.getFileSizeBytes()).thenReturn(1024L);
        when(chunkedUploadService.finalizeUpload(anyString(), anyString(), anyString(),
                anyBoolean(), any())).thenReturn(saved);

        Map<String, Object> result = controller.finalizeUpload(
                "upload-id", false, null, auth, request);
        assertThat(result).containsEntry("id", 1L);
        assertThat(result).containsEntry("filename", "doc.pdf");
    }

    // ── requestDownload ───────────────────────────────────────────────────────

    @Test
    void requestDownloadRedirectsToDownloadToken() {
        CampaignAttachment attachment = mock(CampaignAttachment.class);
        when(attachment.getCampaignId()).thenReturn(1L);
        when(attachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));
        doNothing().when(campaignAccessPolicy).requireCampaignAccess(anyLong(), any());
        TempDownloadLink link = mock(TempDownloadLink.class);
        when(link.getToken()).thenReturn("tok-abc-123");
        when(tempLinkService.generate(anyLong(), anyString(), anyString())).thenReturn(link);

        String view = controller.requestDownload(1L, auth, request);
        assertThat(view).isEqualTo("redirect:/files/download/tok-abc-123");
    }

    @Test
    void requestDownloadWithNotFoundAttachmentThrows() {
        when(attachmentRepository.findById(999L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> controller.requestDownload(999L, auth, request))
                .isInstanceOf(StorageException.class);
    }

    // ── download ──────────────────────────────────────────────────────────────

    @Test
    void downloadWithExpiredTokenSendsGoneError() throws Exception {
        when(tempLinkService.resolve(anyString(), anyString()))
                .thenThrow(new LinkExpiredException("expired"));
        controller.download("expired-token", auth, request, response);
        verify(response).sendError(410, "expired");
    }

    @Test
    void downloadWithDeniedAccessSendsForbiddenError() throws Exception {
        when(tempLinkService.resolve(anyString(), anyString()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        controller.download("denied-token", auth, request, response);
        verify(response).sendError(403, "denied");
    }

    // ── history ───────────────────────────────────────────────────────────────

    @Test
    void historyReturnsUploadViewWithVersions() {
        CampaignAttachment current = mock(CampaignAttachment.class);
        when(current.getCampaignId()).thenReturn(1L);
        when(current.getOriginalFilename()).thenReturn("doc.pdf");
        when(attachmentRepository.findById(1L)).thenReturn(Optional.of(current));
        doNothing().when(campaignAccessPolicy).requireCampaignAccess(anyLong(), any());
        when(attachmentRepository.findByOriginalFilenameAndCampaignIdOrderByVersionDesc(
                anyString(), anyLong())).thenReturn(List.of());

        String view = controller.history(1L, auth, model);
        assertThat(view).isEqualTo("upload/upload");
        verify(model).addAttribute("current", current);
    }
}

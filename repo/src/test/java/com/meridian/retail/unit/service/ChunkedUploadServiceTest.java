package com.meridian.retail.unit.service;

import com.meridian.retail.service.*;
import com.meridian.retail.security.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.AttachmentStatus;
import com.meridian.retail.entity.UploadSession;
import com.meridian.retail.repository.CampaignAttachmentRepository;
import com.meridian.retail.repository.UploadSessionRepository;
import com.meridian.retail.storage.ChunkedUploadService;
import com.meridian.retail.storage.FileValidationService;
import com.meridian.retail.storage.StorageException;
import com.meridian.retail.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkedUploadServiceTest {

    @Mock UploadSessionRepository sessionRepository;
    @Mock CampaignAttachmentRepository attachmentRepository;
    @Mock StorageService storageService;
    @Mock FileValidationService fileValidationService;
    @Mock AuditLogService auditLogService;
    @org.mockito.Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks ChunkedUploadService svc;

    @Test
    void initRejectsNonPositiveTotalChunks() {
        assertThatThrownBy(() -> svc.initUpload(1L, "f.pdf", 0, "ops"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("totalChunks");
    }

    @Test
    void initStartsSessionInProgress() {
        when(storageService.getTempDir(anyString())).thenReturn(Path.of("/tmp/x"));
        when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> {
            UploadSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        UploadSession s = svc.initUpload(1L, "f.pdf", 3, "ops");
        assertThat(s.getStatus()).isEqualTo(AttachmentStatus.IN_PROGRESS);
        assertThat(s.getTotalChunks()).isEqualTo(3);
        assertThat(s.getUploadId()).isNotBlank();
        assertThat(s.getStartedBy()).isEqualTo("ops");
    }

    @Test
    void receiveChunkRejectsOutOfRangeIndex() {
        UploadSession s = UploadSession.builder()
                .uploadId("u1").totalChunks(3)
                .status(AttachmentStatus.IN_PROGRESS).receivedChunks("[]").build();
        when(sessionRepository.findByUploadId("u1")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> svc.receiveChunk("u1", 5, new byte[]{1}))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void finalizeRefusesIncompleteUpload() {
        UploadSession s = UploadSession.builder()
                .uploadId("u2").totalChunks(3)
                .status(AttachmentStatus.IN_PROGRESS).receivedChunks("[0,1]").build();
        when(sessionRepository.findByUploadId("u2")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> svc.finalizeUpload("u2", "ops", "127.0.0.1"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("missing chunks");
    }
}

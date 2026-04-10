package com.meridian.retail.integration;

import com.meridian.retail.entity.CampaignAttachment;
import com.meridian.retail.entity.TempDownloadLink;
import com.meridian.retail.entity.UploadSession;
import com.meridian.retail.repository.TempDownloadLinkRepository;
import com.meridian.retail.storage.ChunkedUploadService;
import com.meridian.retail.storage.LinkExpiredException;
import com.meridian.retail.storage.TempDownloadLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadIntegrationTest extends AbstractIntegrationTest {

    @Autowired ChunkedUploadService chunkedUploadService;
    @Autowired TempDownloadLinkService tempLinkService;
    @Autowired TempDownloadLinkRepository tempLinkRepository;

    @Test
    void chunkedUploadAndDownloadRoundTrip() throws Exception {
        // Build a tiny in-memory PDF (uses PDFBox so the bytes are real and pass Tika)
        byte[] pdfBytes;
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument();
             var out = new ByteArrayOutputStream()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(out);
            pdfBytes = out.toByteArray();
        }

        // Split into 3 chunks
        int chunkSize = pdfBytes.length / 3 + 1;
        UploadSession session = chunkedUploadService.initUpload(1L, "test.pdf", 3, "ops");
        for (int i = 0; i < 3; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, pdfBytes.length);
            byte[] slice = new byte[end - start];
            System.arraycopy(pdfBytes, start, slice, 0, end - start);
            chunkedUploadService.receiveChunk(session.getUploadId(), i, slice);
        }

        CampaignAttachment saved = chunkedUploadService.finalizeUpload(session.getUploadId(), "ops", "127.0.0.1");
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSha256Checksum()).hasSize(64);
        assertThat(saved.getFileType()).isEqualTo("application/pdf");
        assertThat(Files.exists(Path.of(saved.getStoredPath()))).isTrue();

        // Generate a temp link, resolve as the same user, then assert single-use replay fails.
        TempDownloadLink link = tempLinkService.generate(saved.getId(), "ops", "127.0.0.1");
        var resolved = tempLinkService.resolve(link.getToken(), "ops");
        assertThat(resolved.attachment().getId()).isEqualTo(saved.getId());

        assertThatThrownBy(() -> tempLinkService.resolve(link.getToken(), "ops"))
                .isInstanceOf(LinkExpiredException.class);

        // An expired link should also fail
        TempDownloadLink expired = tempLinkService.generate(saved.getId(), "ops", "127.0.0.1");
        ReflectionTestUtils.setField(expired, "expiresAt", LocalDateTime.now().minusMinutes(1));
        tempLinkRepository.save(expired);
        assertThatThrownBy(() -> tempLinkService.resolve(expired.getToken(), "ops"))
                .isInstanceOf(LinkExpiredException.class);

        // Cleanup
        Files.deleteIfExists(Path.of(saved.getStoredPath()));
    }
}

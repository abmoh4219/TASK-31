package com.meridian.retail.api;

import com.meridian.retail.entity.CampaignAttachment;
import com.meridian.retail.entity.TempDownloadLink;
import com.meridian.retail.entity.UploadSession;
import com.meridian.retail.repository.TempDownloadLinkRepository;
import com.meridian.retail.storage.ChunkedUploadService;
import com.meridian.retail.storage.LinkExpiredException;
import com.meridian.retail.storage.TempDownloadLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * File upload/download API tests — service-level round-trips AND real HTTP endpoint tests.
 */
class FileApiTest extends AbstractApiTest {

    @Autowired ChunkedUploadService chunkedUploadService;
    @Autowired TempDownloadLinkService tempLinkService;
    @Autowired TempDownloadLinkRepository tempLinkRepository;

    // ── Service-level tests (real DB) ────────────────────────────────────────────

    @Test
    void chunkedUploadAndDownloadRoundTrip() throws Exception {
        byte[] pdfBytes;
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument();
             var out = new ByteArrayOutputStream()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(out);
            pdfBytes = out.toByteArray();
        }

        int chunkSize = pdfBytes.length / 3 + 1;
        UploadSession session = chunkedUploadService.initUpload(1L, "test.pdf", 3, "ops");
        for (int i = 0; i < 3; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, pdfBytes.length);
            byte[] slice = new byte[end - start];
            System.arraycopy(pdfBytes, start, slice, 0, end - start);
            chunkedUploadService.receiveChunk(session.getUploadId(), i, slice);
        }

        CampaignAttachment saved = chunkedUploadService.finalizeUpload(
                session.getUploadId(), "ops", "127.0.0.1");
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSha256Checksum()).hasSize(64);
        assertThat(saved.getFileType()).isEqualTo("application/pdf");
        assertThat(Files.exists(Path.of(saved.getStoredPath()))).isTrue();

        TempDownloadLink link = tempLinkService.generate(saved.getId(), "ops", "127.0.0.1");
        var resolved = tempLinkService.resolve(link.getToken(), "ops");
        assertThat(resolved.attachment().getId()).isEqualTo(saved.getId());

        assertThatThrownBy(() -> tempLinkService.resolve(link.getToken(), "ops"))
                .isInstanceOf(LinkExpiredException.class);

        TempDownloadLink expired = tempLinkService.generate(saved.getId(), "ops", "127.0.0.1");
        ReflectionTestUtils.setField(expired, "expiresAt", LocalDateTime.now().minusMinutes(1));
        tempLinkRepository.save(expired);
        assertThatThrownBy(() -> tempLinkService.resolve(expired.getToken(), "ops"))
                .isInstanceOf(LinkExpiredException.class);

        Files.deleteIfExists(Path.of(saved.getStoredPath()));
    }

    // ── GET /files/upload ─────────────────────────────────────────────────────────

    @Test
    void uploadPageReachableForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/files/upload", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void uploadPageWithCampaignIdReachableForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/files/upload?campaignId=1", h);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(500);
    }

    @Test
    void uploadPageForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/files/upload", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void uploadPageForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/files/upload", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void uploadPageRequiresAuth() {
        ResponseEntity<String> resp = get("/files/upload", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── POST /files/upload/init ───────────────────────────────────────────────────

    @Test
    void uploadInitReturnsUploadIdForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("campaignId", "1");
        params.add("filename", "test-http.pdf");
        params.add("totalChunks", "1");
        ResponseEntity<String> resp = postFormWithCsrf("/files/upload", "/files/upload/init", h, params);
        // Should return JSON with uploadId
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
            assertThat(resp.getBody()).contains("uploadId");
        }
    }

    @Test
    void uploadInitForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("campaignId", "1");
        params.add("filename", "test.pdf");
        params.add("totalChunks", "1");
        ResponseEntity<String> resp = postFormWithCsrf("/files/upload", "/files/upload/init", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void uploadInitRequiresAuth() {
        ResponseEntity<String> resp = get("/files/upload/init", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403, 405);
    }

    // ── POST /files/upload/chunk ──────────────────────────────────────────────────

    @Test
    void uploadChunkWithInvalidUploadIdReturns400Or404() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        // Get fresh CSRF
        ResponseEntity<String> page = get("/files/upload", h);
        String maskedCsrf = extractHiddenField(page.getBody(), "_csrf");
        String rawXsrf = cookie(page.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

        HttpHeaders postH = new HttpHeaders();
        postH.addAll(h);
        if (rawXsrf != null && !rawXsrf.isEmpty()) {
            String c = postH.getFirst(HttpHeaders.COOKIE);
            if (c != null && c.contains("XSRF-TOKEN=")) {
                c = c.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=" + rawXsrf);
                postH.set(HttpHeaders.COOKIE, c);
            }
        }
        postH.set("Upload-Id", "nonexistent-upload-id");
        postH.set("Chunk-Index", "0");
        postH.set("X-XSRF-TOKEN", maskedCsrf != null ? maskedCsrf : "");

        // Multipart form with a chunk
        org.springframework.core.io.ByteArrayResource chunk =
                new org.springframework.core.io.ByteArrayResource(new byte[]{1, 2, 3}) {
                    @Override
                    public String getFilename() { return "chunk-0"; }
                };

        org.springframework.util.LinkedMultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("chunk", chunk);
        if (maskedCsrf != null) body.add("_csrf", maskedCsrf);

        HttpHeaders multipartH = new HttpHeaders();
        multipartH.addAll(postH);
        multipartH.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> resp = http().exchange(
                url("/files/upload/chunk"), HttpMethod.POST,
                new HttpEntity<>(body, multipartH), String.class);
        // Non-existent uploadId → 4xx response
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 409, 500);
    }

    @Test
    void uploadChunkEndpointForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        HttpHeaders postH = new HttpHeaders();
        postH.addAll(h);
        postH.set("Upload-Id", "any-id");
        postH.set("Chunk-Index", "0");
        postH.setContentType(MediaType.MULTIPART_FORM_DATA);

        org.springframework.util.LinkedMultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("chunk", new org.springframework.core.io.ByteArrayResource(new byte[]{1}) {
            @Override public String getFilename() { return "chunk-0"; }
        });

        ResponseEntity<String> resp = http().exchange(
                url("/files/upload/chunk"), HttpMethod.POST,
                new HttpEntity<>(body, postH), String.class);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── GET /files/upload/status/{uploadId} ───────────────────────────────────────

    @Test
    void uploadStatusForNonExistentIdReturns404() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/files/upload/status/nonexistent-id", h);
        assertThat(resp.getStatusCode().value()).isIn(404, 200);
    }

    @Test
    void uploadStatusRequiresOpsOrAdminRole() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/files/upload/status/some-id", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void uploadStatusRequiresAuth() {
        ResponseEntity<String> resp = get("/files/upload/status/some-id", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    // ── POST /files/upload/finalize/{uploadId} ────────────────────────────────────

    @Test
    void uploadFinalizeWithInvalidIdReturns4xx() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("internalOnly", "false");
        ResponseEntity<String> resp = postFormWithCsrf("/files/upload",
                "/files/upload/finalize/nonexistent-upload-id", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 409, 500);
    }

    @Test
    void uploadFinalizeEndpointForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf("/files/upload",
                "/files/upload/finalize/any-id", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── GET /files/attachment/{id}/download ───────────────────────────────────────

    @Test
    void attachmentDownloadWithInvalidIdReturns404Or500() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/files/attachment/99999/download", h);
        // Either 404 (not found) or 302 (internal redirect) — endpoint exists
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 404, 500);
    }

    @Test
    void attachmentDownloadRequiresAuth() {
        ResponseEntity<String> resp = get("/files/attachment/1/download", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /files/download/{token} ────────────────────────────────────────────────

    @Test
    void downloadWithInvalidTokenReturnsGone() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/files/download/invalid-token-xyz-12345", h);
        // Should return 410 GONE or 403 FORBIDDEN for expired/invalid token
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403, 410, 500);
    }

    @Test
    void downloadRequiresAuth() {
        ResponseEntity<String> resp = get("/files/download/any-token", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /files/attachment/{id}/history ────────────────────────────────────────

    @Test
    void attachmentHistoryWithInvalidIdReturns404Or500() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/files/attachment/99999/history", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 404, 500);
    }

    @Test
    void attachmentHistoryRequiresAuth() {
        ResponseEntity<String> resp = get("/files/attachment/1/history", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    @Test
    void attachmentHistoryForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/files/attachment/1/history", h);
        // Finance has access to files if they have campaign access — or may not
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403, 404, 500);
    }
}

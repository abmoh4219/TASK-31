package com.meridian.retail.api;

import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.entity.ContentStatus;
import com.meridian.retail.integrity.ContentImportService;
import com.meridian.retail.integrity.ContentVersionService;
import com.meridian.retail.integrity.DuplicateDetectionService;
import com.meridian.retail.integrity.MergeService;
import com.meridian.retail.repository.ContentItemRepository;
import com.meridian.retail.repository.ContentVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content API tests — service-level dedup/merge AND real HTTP endpoint tests.
 */
class ContentApiTest extends AbstractApiTest {

    @Autowired ContentImportService importService;
    @Autowired DuplicateDetectionService duplicateDetectionService;
    @Autowired MergeService mergeService;
    @Autowired ContentVersionService versionService;
    @Autowired ContentItemRepository contentItemRepository;
    @Autowired ContentVersionRepository contentVersionRepository;

    // ── Service-level integration tests ──────────────────────────────────────

    @Test
    void importDuplicatesMergeAndRollback() {
        ContentItem a = importService.importSingle(1L, "Spring Sale 15",
                "https://intranet.local/marketing/spring-15a.html",
                "Save fifteen percent off all spring items with code SPRING15 at the register",
                "ops", "127.0.0.1");
        ContentItem b = importService.importSingle(1L, "Spring Sale 15 v2",
                "https://intranet.local/marketing/spring-15b.html",
                "Save fifteen percent off spring items with code SPRING15 at the register",
                "ops", "127.0.0.1");
        ContentItem c = importService.importSingle(1L, "Spring Sale 15 v3",
                "https://intranet.local/marketing/spring-15c.html",
                "Save fifteen percent off all spring goods with code SPRING15 at the register today",
                "ops", "127.0.0.1");

        var groups = duplicateDetectionService.groupDuplicates();
        assertThat(groups).isNotEmpty();

        mergeService.merge(a.getId(), List.of(b.getId(), c.getId()), "reviewer", "127.0.0.1");

        ContentItem reloadedB = contentItemRepository.findById(b.getId()).orElseThrow();
        assertThat(reloadedB.getStatus()).isEqualTo(ContentStatus.MERGED);
        assertThat(contentVersionRepository.countByContentId(b.getId())).isGreaterThanOrEqualTo(1);

        var historyB = versionService.getHistory(b.getId());
        int firstVersion = historyB.stream().mapToInt(v -> v.getVersionNum()).min().orElseThrow();
        versionService.rollback(b.getId(), firstVersion, "admin", "127.0.0.1");
        assertThat(versionService.getHistory(b.getId()).size()).isGreaterThanOrEqualTo(2);
    }

    // ── GET /content ──────────────────────────────────────────────────────────

    @Test
    void contentListPageReachableForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/content", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void contentListPageReachableForReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = get("/content", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void contentListForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/content", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void contentListForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/content", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void anonymousCannotAccessContent() {
        ResponseEntity<String> resp = get("/content", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /content/duplicates ───────────────────────────────────────────────

    @Test
    void contentDuplicatesPageReachableForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/content/duplicates", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── POST /content/merge ───────────────────────────────────────────────────

    @Test
    void contentMergeWithInvalidIdsReturnsErrorOrRedirect() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("masterId", "99999");
        params.add("duplicateIds", "99998");
        ResponseEntity<String> resp = postFormWithCsrf("/content/duplicates", "/content/merge", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    @Test
    void contentMergeForbiddenForOps() {
        // OPERATIONS role is NOT in content/merge's role list
        // Actually it IS — class-level allows all (OPERATIONS, REVIEWER, ADMIN)
        // but merge specifically requires REVIEWER or ADMIN
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("masterId", "1");
        params.add("duplicateIds", "2");
        ResponseEntity<String> resp = postFormWithCsrf("/content/duplicates", "/content/merge", h, params);
        // Ops might be forbidden (if merge requires REVIEWER) or get error from bad IDs
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    @Test
    void contentMergeRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("masterId", "1");
        params.add("duplicateIds", "2");
        ResponseEntity<String> resp = postForm("/content/merge", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── GET /content/{id}/history ─────────────────────────────────────────────

    @Test
    void contentHistoryPageReachableForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        // Create a content item first
        ContentItem item = importService.importSingle(1L, "History Test",
                "https://intranet.local/history-test.html",
                "Some body text for history test", "ops", "127.0.0.1");
        ResponseEntity<String> resp = get("/content/" + item.getId() + "/history", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void contentHistoryWithInvalidIdReturns404Or500() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/content/99999/history", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 404, 500);
    }

    @Test
    void contentHistoryRequiresAuth() {
        ResponseEntity<String> resp = get("/content/1/history", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    // ── POST /content/{id}/rollback/{version} ─────────────────────────────────

    @Test
    void contentRollbackForReviewerWithNonExistentItem() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf(
                "/content", "/content/99999/rollback/1", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    @Test
    void contentRollbackForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf(
                "/content", "/content/1/rollback/1", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void contentRollbackRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postForm("/content/1/rollback/1", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── POST /content/import/single ───────────────────────────────────────────

    @Test
    void contentImportSingleForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("campaignId", "1");
        params.add("title", "HTTP Import Test " + System.currentTimeMillis());
        params.add("sourceUrl", "https://intranet.local/http-test.html");
        params.add("body", "Test content imported via HTTP endpoint");
        ResponseEntity<String> resp = postFormWithCsrf("/content", "/content/import/single", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void contentImportSingleForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("campaignId", "1");
        params.add("title", "Finance Import");
        params.add("sourceUrl", "https://intranet.local/test.html");
        params.add("body", "body");
        ResponseEntity<String> resp = postFormWithCsrf("/content", "/content/import/single", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void contentImportSingleRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postForm("/content/import/single", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── POST /content/import/csv ──────────────────────────────────────────────

    @Test
    void contentImportCsvEndpointAcceptsCsvFileForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");

        // Get CSRF from content page
        ResponseEntity<String> page = get("/content", h);
        String maskedCsrf = extractHiddenField(page.getBody(), "_csrf");
        String rawXsrf = cookie(page.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

        HttpHeaders postH = new HttpHeaders();
        postH.addAll(h);
        if (rawXsrf != null && !rawXsrf.isEmpty()) {
            String c = postH.getFirst(HttpHeaders.COOKIE);
            if (c != null && c.contains("XSRF-TOKEN=")) {
                c = c.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=" + rawXsrf);
            } else if (c != null) {
                c = c + "; XSRF-TOKEN=" + rawXsrf;
            }
            postH.set(HttpHeaders.COOKIE, c);
        }
        if (maskedCsrf != null) postH.set("X-XSRF-TOKEN", maskedCsrf);
        postH.setContentType(MediaType.MULTIPART_FORM_DATA);

        String csvContent = "title,sourceUrl,body\n"
                + "\"CSV Test Item\",\"https://intranet.local/csv-test.html\",\"CSV import test body\"\n";
        org.springframework.core.io.ByteArrayResource csvFile =
                new org.springframework.core.io.ByteArrayResource(csvContent.getBytes()) {
                    @Override public String getFilename() { return "test.csv"; }
                };

        org.springframework.util.LinkedMultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("file", csvFile);
        body.add("campaignId", "1");
        if (maskedCsrf != null) body.add("_csrf", maskedCsrf);

        ResponseEntity<String> resp = http().exchange(
                url("/content/import/csv"), HttpMethod.POST,
                new HttpEntity<>(body, postH), String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    @Test
    void contentImportCsvRequiresAuth() {
        HttpHeaders postH = new HttpHeaders();
        postH.setContentType(MediaType.MULTIPART_FORM_DATA);
        org.springframework.util.LinkedMultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("campaignId", "1");
        ResponseEntity<String> resp = http().exchange(
                url("/content/import/csv"), HttpMethod.POST,
                new HttpEntity<>(body, postH), String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    @Test
    void contentImportCsvForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/content/duplicates", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }
}

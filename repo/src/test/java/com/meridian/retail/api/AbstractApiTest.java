package com.meridian.retail.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

/**
 * Base for all API tests. Starts a real HTTP server on a random port and provides
 * real HTTP helpers for form-based login, CSRF handling, and signed admin requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractApiTest {

    private static final String EXTERNAL_URL = System.getenv("IT_DATASOURCE_URL");
    private static final boolean USE_EXTERNAL = EXTERNAL_URL != null && !EXTERNAL_URL.isBlank();
    private static MySQLContainer<?> MYSQL;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        if (USE_EXTERNAL) {
            r.add("spring.datasource.url", () -> EXTERNAL_URL);
            r.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("IT_DATASOURCE_USERNAME", "retail_user"));
            r.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("IT_DATASOURCE_PASSWORD", "retail_pass"));
        } else {
            if (MYSQL == null) {
                MYSQL = new MySQLContainer<>("mysql:8.0")
                        .withDatabaseName("retail_campaign_test")
                        .withUsername("retail_user")
                        .withPassword("retail_pass")
                        .withCommand("--log-bin-trust-function-creators=ON");
                MYSQL.start();
                Runtime.getRuntime().addShutdownHook(new Thread(MYSQL::stop));
            }
            r.add("spring.datasource.url", MYSQL::getJdbcUrl);
            r.add("spring.datasource.username", MYSQL::getUsername);
            r.add("spring.datasource.password", MYSQL::getPassword);
        }
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @LocalServerPort
    protected int port;

    private volatile RestTemplate client;

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** RestTemplate that does NOT follow redirects and does NOT throw on 4xx/5xx. */
    protected RestTemplate http() {
        if (client == null) {
            org.springframework.http.client.SimpleClientHttpRequestFactory f =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection c, String m) throws IOException {
                    super.prepareConnection(c, m);
                    c.setInstanceFollowRedirects(false);
                }
            };
            RestTemplate t = new RestTemplate(f);
            t.setErrorHandler(new DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(HttpStatusCode status) { return false; }
            });
            client = t;
        }
        return client;
    }

    /**
     * Real form-based login. Returns HttpHeaders with JSESSIONID + XSRF-TOKEN cookies
     * ready for subsequent requests.
     */
    protected HttpHeaders loginAs(String username, String password) {
        ResponseEntity<String> loginPage = http().getForEntity(url("/login"), String.class);
        List<String> sc1 = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);
        String session1 = cookie(sc1, "JSESSIONID");
        String csrfRaw = cookie(sc1, "XSRF-TOKEN");
        String csrfFromHtml = extractHiddenField(loginPage.getBody(), "_csrf");
        String csrfToSubmit = csrfFromHtml != null ? csrfFromHtml : csrfRaw;

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (session1 != null || csrfRaw != null) {
            StringBuilder cb = new StringBuilder();
            if (session1 != null) cb.append("JSESSIONID=").append(session1).append("; ");
            if (csrfRaw != null) cb.append("XSRF-TOKEN=").append(csrfRaw);
            h.add(HttpHeaders.COOKIE, stripTrailingSemicolon(cb.toString()));
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", username);
        body.add("password", password);
        if (csrfToSubmit != null) body.add("_csrf", csrfToSubmit);

        ResponseEntity<String> loginResp = http().exchange(
                url("/login"), HttpMethod.POST, new HttpEntity<>(body, h), String.class);

        List<String> sc2 = loginResp.getHeaders().get(HttpHeaders.SET_COOKIE);
        String session2 = cookie(sc2, "JSESSIONID");
        String csrf2 = cookie(sc2, "XSRF-TOKEN");
        if (session2 == null) session2 = session1;
        if (csrf2 == null || csrf2.isEmpty()) csrf2 = csrfRaw;

        HttpHeaders auth = new HttpHeaders();
        StringBuilder cb = new StringBuilder();
        if (session2 != null) cb.append("JSESSIONID=").append(session2).append("; ");
        if (csrf2 != null) cb.append("XSRF-TOKEN=").append(csrf2);
        auth.add(HttpHeaders.COOKIE, stripTrailingSemicolon(cb.toString()));
        if (csrf2 != null) auth.add("X-XSRF-TOKEN", csrf2);
        return auth;
    }

    protected ResponseEntity<String> get(String path, HttpHeaders headers) {
        return http().exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    protected ResponseEntity<String> postForm(String path, HttpHeaders authHeaders,
                                               MultiValueMap<String, String> params) {
        HttpHeaders h = new HttpHeaders();
        if (authHeaders != null) h.addAll(authHeaders);
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return http().exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(params, h), String.class);
    }

    /**
     * GETs formPage to get fresh CSRF, then POSTs to submitPath with it.
     * Handles Spring Security 6 XOR CSRF.
     */
    protected ResponseEntity<String> postFormWithCsrf(String formPage, String submitPath,
                                                       HttpHeaders authHeaders,
                                                       MultiValueMap<String, String> params) {
        ResponseEntity<String> page = get(formPage, authHeaders);
        String maskedCsrf = extractHiddenField(page.getBody(), "_csrf");
        String newXsrfToken = cookie(page.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

        HttpHeaders updatedH = new HttpHeaders();
        if (authHeaders != null) updatedH.addAll(authHeaders);

        if (newXsrfToken != null && !newXsrfToken.isEmpty()) {
            String existingCookie = updatedH.getFirst(HttpHeaders.COOKIE);
            String freshCookie;
            if (existingCookie != null && !existingCookie.isEmpty()) {
                if (existingCookie.contains("XSRF-TOKEN=")) {
                    freshCookie = existingCookie.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=" + newXsrfToken);
                } else {
                    freshCookie = existingCookie + "; XSRF-TOKEN=" + newXsrfToken;
                }
            } else {
                freshCookie = "XSRF-TOKEN=" + newXsrfToken;
            }
            updatedH.set(HttpHeaders.COOKIE, freshCookie);
        }

        MultiValueMap<String, String> withCsrf = new LinkedMultiValueMap<>(params);
        if (maskedCsrf != null) withCsrf.add("_csrf", maskedCsrf);
        return postForm(submitPath, updatedH, withCsrf);
    }

    /**
     * POST with a JSON body. Gets a fresh CSRF token via a GET /campaigns call first,
     * then sends it as X-XSRF-TOKEN header (AJAX style).
     */
    protected ResponseEntity<String> postJson(String path, HttpHeaders authHeaders, String jsonBody) {
        // Get fresh XSRF-TOKEN from any authenticated page.
        ResponseEntity<String> anyPage = get("/campaigns", authHeaders);
        String maskedCsrf = extractHiddenField(anyPage.getBody(), "_csrf");
        String rawXsrf = cookie(anyPage.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

        HttpHeaders h = new HttpHeaders();
        if (authHeaders != null) h.addAll(authHeaders);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (rawXsrf != null && !rawXsrf.isEmpty()) {
            String c = h.getFirst(HttpHeaders.COOKIE);
            if (c != null && c.contains("XSRF-TOKEN=")) {
                c = c.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=" + rawXsrf);
            } else if (c != null) {
                c = c + "; XSRF-TOKEN=" + rawXsrf;
            } else {
                c = "XSRF-TOKEN=" + rawXsrf;
            }
            h.set(HttpHeaders.COOKIE, c);
        }
        if (maskedCsrf != null) h.set("X-XSRF-TOKEN", maskedCsrf);

        return http().exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(jsonBody, h), String.class);
    }

    /**
     * Full signed POST for privileged admin/approval endpoints.
     * 1. Gets a fresh nonce from /admin/nonce or /approval/nonce.
     * 2. Fetches a server-issued HMAC signature from /admin/sign-form or /approval/sign-form.
     * 3. POSTs to targetPath with _nonce + _timestamp + _signature + CSRF + extraParams.
     *
     * Returns the HTTP response from the target endpoint.
     */
    protected ResponseEntity<String> signedPost(String targetPath, HttpHeaders authHeaders,
                                                  MultiValueMap<String, String> extraParams) {
        boolean isAdmin = targetPath.startsWith("/admin/");
        String nonceEndpoint = isAdmin ? "/admin/nonce" : "/approval/nonce";
        String signEndpoint = isAdmin ? "/admin/sign-form" : "/approval/sign-form";

        // 1. Get nonce (GET request — no CSRF needed).
        HttpHeaders jsonH = new HttpHeaders();
        if (authHeaders != null) jsonH.addAll(authHeaders);
        jsonH.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        ResponseEntity<String> nonceResp = http().exchange(
                url(nonceEndpoint), HttpMethod.GET, new HttpEntity<>(jsonH), String.class);
        if (!nonceResp.getStatusCode().is2xxSuccessful()) return nonceResp;
        String nonce = extractJsonField(nonceResp.getBody(), "nonce");
        String timestamp = extractJsonField(nonceResp.getBody(), "timestamp");
        if (nonce == null || timestamp == null) return nonceResp;

        // 2. Get signature via sign-form (JSON POST — needs CSRF).
        String signPayload = "{\"method\":\"POST\",\"path\":\"" + targetPath
                + "\",\"timestamp\":\"" + timestamp
                + "\",\"nonce\":\"" + nonce + "\"}";
        ResponseEntity<String> signResp = postJson(signEndpoint, authHeaders, signPayload);
        if (!signResp.getStatusCode().is2xxSuccessful()) return signResp;
        String signature = extractJsonField(signResp.getBody(), "signature");
        if (signature == null) return signResp;

        // 3. Get fresh CSRF for the actual form POST.
        ResponseEntity<String> anyPage = get("/campaigns", authHeaders);
        String maskedCsrf = extractHiddenField(anyPage.getBody(), "_csrf");
        String rawXsrf = cookie(anyPage.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

        HttpHeaders updatedH = new HttpHeaders();
        if (authHeaders != null) updatedH.addAll(authHeaders);
        if (rawXsrf != null && !rawXsrf.isEmpty()) {
            String c = updatedH.getFirst(HttpHeaders.COOKIE);
            if (c != null && c.contains("XSRF-TOKEN=")) {
                c = c.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=" + rawXsrf);
            } else if (c != null) {
                c = c + "; XSRF-TOKEN=" + rawXsrf;
            }
            updatedH.set(HttpHeaders.COOKIE, c);
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (extraParams != null) params.addAll(extraParams);
        params.add("_nonce", nonce);
        params.add("_timestamp", timestamp);
        params.add("_signature", signature);
        if (maskedCsrf != null) params.add("_csrf", maskedCsrf);

        return postForm(targetPath, updatedH, params);
    }

    /** PUT with a form body, CSRF extracted from formPage. */
    protected ResponseEntity<String> putFormWithCsrf(String formPage, String submitPath,
                                                       HttpHeaders authHeaders,
                                                       MultiValueMap<String, String> params) {
        ResponseEntity<String> page = get(formPage, authHeaders);
        String maskedCsrf = extractHiddenField(page.getBody(), "_csrf");
        String newXsrfToken = cookie(page.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

        HttpHeaders updatedH = new HttpHeaders();
        if (authHeaders != null) updatedH.addAll(authHeaders);
        if (newXsrfToken != null && !newXsrfToken.isEmpty()) {
            String existingCookie = updatedH.getFirst(HttpHeaders.COOKIE);
            String freshCookie;
            if (existingCookie != null && existingCookie.contains("XSRF-TOKEN=")) {
                freshCookie = existingCookie.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=" + newXsrfToken);
            } else {
                freshCookie = (existingCookie != null ? existingCookie + "; " : "") + "XSRF-TOKEN=" + newXsrfToken;
            }
            updatedH.set(HttpHeaders.COOKIE, freshCookie);
        }

        HttpHeaders h = new HttpHeaders();
        h.addAll(updatedH);
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> withCsrf = new LinkedMultiValueMap<>(params);
        if (maskedCsrf != null) withCsrf.add("_csrf", maskedCsrf);
        return http().exchange(url(submitPath), HttpMethod.PUT,
                new HttpEntity<>(withCsrf, h), String.class);
    }

    /** DELETE request with CSRF from formPage. */
    protected ResponseEntity<String> deleteWithCsrf(String formPage, String submitPath,
                                                      HttpHeaders authHeaders) {
        ResponseEntity<String> page = get(formPage, authHeaders);
        String maskedCsrf = extractHiddenField(page.getBody(), "_csrf");
        String newXsrfToken = cookie(page.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

        HttpHeaders updatedH = new HttpHeaders();
        if (authHeaders != null) updatedH.addAll(authHeaders);
        if (newXsrfToken != null && !newXsrfToken.isEmpty()) {
            String existingCookie = updatedH.getFirst(HttpHeaders.COOKIE);
            String freshCookie;
            if (existingCookie != null && existingCookie.contains("XSRF-TOKEN=")) {
                freshCookie = existingCookie.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=" + newXsrfToken);
            } else {
                freshCookie = (existingCookie != null ? existingCookie + "; " : "") + "XSRF-TOKEN=" + newXsrfToken;
            }
            updatedH.set(HttpHeaders.COOKIE, freshCookie);
        }

        HttpHeaders h = new HttpHeaders();
        h.addAll(updatedH);
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (maskedCsrf != null) params.add("_csrf", maskedCsrf);
        return http().exchange(url(submitPath), HttpMethod.DELETE,
                new HttpEntity<>(params, h), String.class);
    }

    /** Extract a simple field from a JSON string (supports both string and numeric values). */
    protected String extractJsonField(String json, String key) {
        if (json == null) return null;
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int start = idx + pattern.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first == '"') {
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}'
                    && json.charAt(end) != ']') end++;
            return json.substring(start, end).trim();
        }
    }

    protected String extractHiddenField(String html, String fieldName) {
        if (html == null) return null;
        String inputVal = extractTagAttributeValue(html, "name=\"" + fieldName + "\"", "value");
        if (inputVal != null) return inputVal;
        inputVal = extractTagAttributeValue(html, "name='" + fieldName + "'", "value");
        if (inputVal != null) return inputVal;
        String metaVal = extractTagAttributeValue(html, "name=\"" + fieldName + "\"", "content");
        if (metaVal != null) return metaVal;
        return extractTagAttributeValue(html, "name='" + fieldName + "'", "content");
    }

    private String extractTagAttributeValue(String html, String namePattern, String attrName) {
        int nameIdx = html.indexOf(namePattern);
        if (nameIdx < 0) return null;
        int tagStart = html.lastIndexOf('<', nameIdx);
        int tagEnd = html.indexOf('>', nameIdx);
        if (tagStart < 0 || tagEnd < 0) return null;
        String tag = html.substring(tagStart, tagEnd + 1);
        String seek = attrName + "=\"";
        int vIdx = tag.indexOf(seek);
        if (vIdx >= 0) {
            int start = vIdx + seek.length();
            int end = tag.indexOf('"', start);
            if (end > start) return tag.substring(start, end);
        }
        seek = attrName + "='";
        vIdx = tag.indexOf(seek);
        if (vIdx >= 0) {
            int start = vIdx + seek.length();
            int end = tag.indexOf('\'', start);
            if (end > start) return tag.substring(start, end);
        }
        return null;
    }

    protected String cookie(List<String> setCookies, String name) {
        if (setCookies == null) return null;
        for (String sc : setCookies) {
            for (String part : sc.split(";")) {
                String t = part.trim();
                if (t.startsWith(name + "=")) return t.substring(name.length() + 1);
            }
        }
        return null;
    }

    private String stripTrailingSemicolon(String s) {
        return s.replaceAll(";\\s*$", "").trim();
    }
}

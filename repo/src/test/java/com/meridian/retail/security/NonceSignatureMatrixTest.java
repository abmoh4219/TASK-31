package com.meridian.retail.security;

import com.meridian.retail.entity.UsedNonce;
import com.meridian.retail.repository.UsedNonceRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MEDIUM #7: positive + negative matrix for the anti-replay + signing filters on a
 * privileged JSON POST under /admin/**.
 *
 * Scenarios:
 *   1. Valid nonce + timestamp + signature            -> passes
 *   2. Missing X-Nonce                                 -> rejected
 *   3. Invalid X-Signature                             -> rejected
 *   4. Replayed nonce (already in used_nonces)         -> rejected
 *   5. Expired timestamp (outside ±5 min window)       -> rejected
 *
 * The tests run the filters directly rather than through MockMvc so we don't need a full
 * Spring context or a MySQL dependency.
 */
@ExtendWith(MockitoExtension.class)
class NonceSignatureMatrixTest {

    private static final String SIGNING_KEY = "retail-campaign-hmac-signing-key!!";

    @Mock UsedNonceRepository usedNonceRepository;
    @Mock FilterChain chain;

    private MockHttpServletRequest jsonAdminPost(String nonce, String timestamp,
                                                 String signature, String body) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/admin/api/secret");
        req.setContentType("application/json");
        if (nonce != null) req.addHeader("X-Nonce", nonce);
        if (timestamp != null) req.addHeader("X-Timestamp", timestamp);
        if (signature != null) req.addHeader("X-Signature", signature);
        req.setContent(body.getBytes(StandardCharsets.UTF_8));
        return req;
    }

    private String hmacSha256Hex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RequestSigningFilter signingFilter() {
        RequestSigningFilter f = new RequestSigningFilter();
        ReflectionTestUtils.setField(f, "signingSecret", SIGNING_KEY);
        return f;
    }

    @Test
    void validRequestPassesBothFilters() throws Exception {
        String nonce = "nonce-abc-123";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String body = "{\"x\":1}";
        String canonical = "POST\n/admin/api/secret\n" + timestamp + "\n" + sha256Hex(body.getBytes());
        String signature = hmacSha256Hex(canonical);

        when(usedNonceRepository.existsByNonce(nonce)).thenReturn(false);

        NonceValidationFilter nonceF = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req1 = jsonAdminPost(nonce, timestamp, signature, body);
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        nonceF.doFilter(req1, res1, chain);
        verify(chain).doFilter(req1, res1);

        RequestSigningFilter signF = signingFilter();
        MockHttpServletRequest req2 = jsonAdminPost(nonce, timestamp, signature, body);
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        signF.doFilter(req2, res2, chain);
        org.assertj.core.api.Assertions.assertThat(res2.getStatus()).isNotEqualTo(403);
    }

    @Test
    void missingNonceIsRejected() throws Exception {
        NonceValidationFilter nonceF = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = jsonAdminPost(null, String.valueOf(System.currentTimeMillis()),
                "dummy", "{}");
        MockHttpServletResponse res = new MockHttpServletResponse();

        nonceF.doFilter(req, res, chain);

        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String body = "{\"x\":1}";

        RequestSigningFilter signF = signingFilter();
        MockHttpServletRequest req = jsonAdminPost("n1", timestamp, "deadbeef", body);
        MockHttpServletResponse res = new MockHttpServletResponse();

        signF.doFilter(req, res, chain);

        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void replayedNonceIsRejected() throws Exception {
        when(usedNonceRepository.existsByNonce("reused")).thenReturn(true);

        NonceValidationFilter nonceF = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = jsonAdminPost("reused",
                String.valueOf(System.currentTimeMillis()), "sig", "{}");
        MockHttpServletResponse res = new MockHttpServletResponse();

        nonceF.doFilter(req, res, chain);

        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void expiredTimestampIsRejected() throws Exception {
        long tenMinAgo = System.currentTimeMillis() - (10 * 60 * 1000L);
        NonceValidationFilter nonceF = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = jsonAdminPost("n2", String.valueOf(tenMinAgo), "sig", "{}");
        MockHttpServletResponse res = new MockHttpServletResponse();

        nonceF.doFilter(req, res, chain);

        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(req, res);
    }
}

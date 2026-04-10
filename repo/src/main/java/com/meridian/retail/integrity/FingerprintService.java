package com.meridian.retail.integrity;

import com.google.common.hash.Hashing;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Content fingerprinting and URL normalization.
 *
 *   - SHA-256 of the body text -> exact-duplicate detection (hex string).
 *   - SimHash (64-bit, Charikar) of the body text -> near-duplicate detection.
 *     Two documents are considered near-duplicates when Hamming distance <= 8.
 *   - URL normalization: lowercase, strip tracking params (utm_*, fbclid, gclid),
 *     percent-decode, drop trailing slash, drop default ports.
 *
 * Implementation notes:
 *   - SimHash uses 64-bit Murmur3 from Guava (16-byte) per token, but we only need
 *     64 bits — we take the first 8 bytes of the digest. For each of the 64 bit
 *     positions we add +1 if the token's bit is 1 and -1 if it is 0; the final
 *     fingerprint bit is 1 when the column sum is positive.
 *   - The implementation is intentionally explicit step-by-step so QA can verify
 *     correctness by reading the source.
 */
@Service
public class FingerprintService {

    private static final int SIMHASH_BITS = 64;
    private static final int NEAR_DUPLICATE_HAMMING_THRESHOLD = 8;

    /** Hex SHA-256 of the input. Used for exact duplicate detection. */
    public String computeSha256(String text) {
        if (text == null) text = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 64-bit SimHash (Charikar). Tokens are whitespace-split words; each token's
     * 128-bit Murmur3 hash contributes its first 64 bits to a column accumulator.
     */
    public long computeSimHash(String text) {
        if (text == null || text.isBlank()) return 0L;

        String[] tokens = text.toLowerCase().split("\\s+");
        int[] columns = new int[SIMHASH_BITS];

        for (String token : tokens) {
            if (token.isBlank()) continue;
            // (a) Compute a 128-bit Murmur3 hash via Guava
            byte[] tokenHash = Hashing.murmur3_128()
                    .hashString(token, StandardCharsets.UTF_8)
                    .asBytes();
            // (b) Take the first 8 bytes -> 64 bits
            long h = 0L;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (tokenHash[i] & 0xFFL);
            }
            // (c) For each bit position, +1 or -1
            for (int b = 0; b < SIMHASH_BITS; b++) {
                if (((h >>> b) & 1L) == 1L) columns[b] += 1;
                else                        columns[b] -= 1;
            }
        }

        // (d) Final bit = 1 if column sum > 0 else 0
        long fingerprint = 0L;
        for (int b = 0; b < SIMHASH_BITS; b++) {
            if (columns[b] > 0) fingerprint |= (1L << b);
        }
        return fingerprint;
    }

    /** Hamming distance between two 64-bit longs. */
    public int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /** Returns true if two SimHashes are within the near-duplicate threshold (default 8 bits). */
    public boolean isNearDuplicate(long a, long b) {
        return hammingDistance(a, b) <= NEAR_DUPLICATE_HAMMING_THRESHOLD;
    }

    /** Returns the threshold for callers that want to display "X of 8 bits differ". */
    public int nearDuplicateThreshold() {
        return NEAR_DUPLICATE_HAMMING_THRESHOLD;
    }

    /**
     * URL normalization for dedup:
     *   - lowercase scheme + host
     *   - percent-decode
     *   - remove tracking params (utm_*, fbclid, gclid, mc_*)
     *   - drop trailing slash from the path (except root)
     *   - drop default ports (80 for http, 443 for https)
     */
    public String normalizeUrl(String input) {
        if (input == null || input.isBlank()) return "";
        try {
            String decoded = URLDecoder.decode(input.trim(), StandardCharsets.UTF_8);
            URI uri = URI.create(decoded);

            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            int port = uri.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }

            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String query = uri.getQuery();
            String cleanQuery = stripTrackingParams(query);

            StringBuilder sb = new StringBuilder();
            if (!scheme.isEmpty()) sb.append(scheme).append("://");
            sb.append(host);
            if (port != -1) sb.append(":").append(port);
            sb.append(path);
            if (cleanQuery != null && !cleanQuery.isEmpty()) sb.append("?").append(cleanQuery);
            return sb.toString();
        } catch (Exception e) {
            // If we can't parse it, lowercase it as the best-effort fallback.
            return input.trim().toLowerCase();
        }
    }

    private String stripTrackingParams(String query) {
        if (query == null || query.isEmpty()) return null;
        Map<String, String> kept = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String val = eq < 0 ? "" : pair.substring(eq + 1);
            String lk = key.toLowerCase();
            if (lk.startsWith("utm_") || lk.equals("fbclid") || lk.equals("gclid") || lk.startsWith("mc_")) {
                continue;
            }
            kept.put(key, val);
        }
        if (kept.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        kept.forEach((k, v) -> {
            if (out.length() > 0) out.append("&");
            out.append(k);
            if (!v.isEmpty()) out.append("=").append(v);
        });
        return out.toString();
    }
}

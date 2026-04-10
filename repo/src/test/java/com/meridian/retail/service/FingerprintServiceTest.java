package com.meridian.retail.service;

import com.meridian.retail.integrity.FingerprintService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintServiceTest {

    private final FingerprintService svc = new FingerprintService();

    @Test
    void sameInputProducesSameSha256() {
        String text = "Hello world";
        assertThat(svc.computeSha256(text)).isEqualTo(svc.computeSha256(text));
    }

    @Test
    void differentInputProducesDifferentSha256() {
        assertThat(svc.computeSha256("a")).isNotEqualTo(svc.computeSha256("b"));
    }

    @Test
    void simHashIs64BitLong() {
        long h = svc.computeSimHash("the quick brown fox jumps over the lazy dog");
        // Just confirm it's not always zero — any non-trivial hash will do.
        assertThat(h).isNotZero();
    }

    @Test
    void nearDuplicatesHaveSmallHamming() {
        long a = svc.computeSimHash(
                "Spring is here! Save 15% storewide with code SPRING15 at the register. Offer valid through April 30.");
        long b = svc.computeSimHash(
                "Spring is here! Save 15% storewide with code SPRING15 at the register. Valid through April 30.");
        int dist = svc.hammingDistance(a, b);
        assertThat(dist).isLessThanOrEqualTo(8);
    }

    @Test
    void dissimilarTextsHaveLargeHamming() {
        long a = svc.computeSimHash("Spring is here save fifteen percent storewide");
        long b = svc.computeSimHash(
                "Annual financial report covering quarterly earnings dividend payouts and shareholder equity");
        int dist = svc.hammingDistance(a, b);
        assertThat(dist).isGreaterThan(15);
    }

    @Test
    void normalizeUrlLowercasesAndStripsTracking() {
        String result = svc.normalizeUrl("HTTPS://Example.COM/Path/?utm_source=email&id=42");
        assertThat(result).isEqualTo("https://example.com/Path?id=42");
    }

    @Test
    void normalizeUrlDropsTrailingSlash() {
        assertThat(svc.normalizeUrl("https://example.com/page/")).isEqualTo("https://example.com/page");
    }

    @Test
    void normalizeUrlDropsDefaultPort() {
        assertThat(svc.normalizeUrl("http://example.com:80/p")).isEqualTo("http://example.com/p");
    }
}

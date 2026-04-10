package com.meridian.retail.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AES-256-GCM round-trip + tamper-detection properties.
 */
class EncryptionServiceTest {

    private final EncryptionService svc = new EncryptionService("retail-campaign-aes-key-32chars!!");

    @Test
    void roundTripShortString() {
        String plain = "hunter2";
        String ct = svc.encrypt(plain);
        assertThat(ct).isNotEqualTo(plain);
        assertThat(svc.decrypt(ct)).isEqualTo(plain);
    }

    @Test
    void roundTripLongString() {
        String plain = "a".repeat(5000);
        assertThat(svc.decrypt(svc.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    void roundTripUnicode() {
        String plain = "客户数据/名字: 王小明 — email: test@example.com";
        assertThat(svc.decrypt(svc.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    void nullPassThroughBothDirections() {
        assertThat(svc.encrypt(null)).isNull();
        assertThat(svc.decrypt(null)).isNull();
    }

    @Test
    void sameInputProducesDifferentCiphertextEachCall() {
        // Random IV per encrypt() call -> deterministic encryption is structurally impossible,
        // which also means the encrypted column cannot be used in a WHERE clause.
        String plain = "same-value";
        String ct1 = svc.encrypt(plain);
        String ct2 = svc.encrypt(plain);
        assertThat(ct1).isNotEqualTo(ct2);
        assertThat(svc.decrypt(ct1)).isEqualTo(plain);
        assertThat(svc.decrypt(ct2)).isEqualTo(plain);
    }

    @Test
    void tamperedCiphertextFailsAuth() {
        String ct = svc.encrypt("important");
        // Flip a byte inside the Base64 payload so the GCM tag no longer matches.
        char[] chars = ct.toCharArray();
        // Nudge the last meaningful char by one to corrupt the auth tag.
        int idx = chars.length - 2;
        chars[idx] = chars[idx] == 'A' ? 'B' : 'A';
        String bad = new String(chars);

        assertThatThrownBy(() -> svc.decrypt(bad))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decryptWithWrongKeyFails() {
        String ct = svc.encrypt("important");
        EncryptionService other = new EncryptionService("different-master-key-32-chars---");
        assertThatThrownBy(() -> other.decrypt(ct))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankKeyIsRejected() {
        assertThatThrownBy(() -> new EncryptionService(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EncryptionService(null))
                .isInstanceOf(IllegalStateException.class);
    }
}

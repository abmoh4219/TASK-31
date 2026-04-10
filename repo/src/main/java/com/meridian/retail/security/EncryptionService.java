package com.meridian.retail.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for at-rest sensitive values (HIGH-risk fields on domain
 * entities). Key is sourced from {@code app.encryption.key} — normally supplied via
 * environment variable in docker-compose — and stretched to 256 bits via SHA-256.
 *
 * Output format (Base64 of a single byte blob):
 *   [ 12-byte IV ][ ciphertext || 16-byte GCM tag ]
 *
 * The IV is random per call, so encrypting the same plaintext twice produces different
 * ciphertext — a property asserted by EncryptionServiceTest. A ciphertext produced on
 * one JVM can be decrypted by any other JVM sharing the same master key.
 *
 * We expose a static accessor ({@link #instance()}) because JPA AttributeConverters
 * are instantiated by the persistence provider, not Spring, and cannot receive
 * constructor injection. EncryptedStringConverter pulls the Spring-managed bean via
 * this holder after Spring starts.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static volatile EncryptionService INSTANCE;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(@Value("${app.encryption.key:retail-campaign-aes-key-32chars!!}") String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.key is required for EncryptionService — supply it via the ENCRYPTION_KEY env var");
        }
        try {
            // SHA-256 stretches any input to a 256-bit AES key. This is NOT a password-based
            // KDF (no salt, no iterations) — the raw key is expected to already be high-entropy,
            // stored in a secret manager / env var. SHA-256 is used only to normalize the length.
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(digest, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES key", e);
        }
    }

    @PostConstruct
    void publishInstance() {
        INSTANCE = this;
    }

    /** Static accessor used by EncryptedStringConverter (see class-level javadoc). */
    public static EncryptionService instance() {
        EncryptionService ref = INSTANCE;
        if (ref == null) {
            throw new IllegalStateException(
                    "EncryptionService not initialized — called from outside Spring context?");
        }
        return ref;
    }

    /**
     * Encrypt a UTF-8 string. Returns Base64(IV || ciphertext || tag). Returns null for
     * null input so @Column(nullable=true) fields survive the round-trip.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypt a value previously returned by {@link #encrypt(String)}. Returns null for
     * null input. Throws if the ciphertext is tampered, truncated, or authenticated with
     * a different key (GCM integrity check).
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] blob = Base64.getDecoder().decode(ciphertext);
            if (blob.length < IV_LENGTH_BYTES + 16) {
                throw new IllegalArgumentException("Ciphertext too short to contain IV + tag");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(blob, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] ct = new byte[blob.length - IV_LENGTH_BYTES];
            System.arraycopy(blob, IV_LENGTH_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new IllegalStateException("Decryption failed — ciphertext tampered or wrong key", e);
        }
    }
}

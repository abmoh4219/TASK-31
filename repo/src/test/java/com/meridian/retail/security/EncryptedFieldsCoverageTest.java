package com.meridian.retail.security;

import com.meridian.retail.entity.AnomalyAlert;
import com.meridian.retail.entity.BackupRecord;
import jakarta.persistence.Convert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R4 audit MEDIUM #7: encryption-at-rest must extend beyond {@code BackupRecord.filePath}.
 *
 * Verifies that the additional sensitive fields identified in
 * {@code SecurityDesignDecisions.md} are annotated with the JPA
 * {@link EncryptedStringConverter}, so a JPA write goes through AES-256-GCM before
 * touching the database.
 *
 * Reflection-based rather than DB-based: lets the test run without a real MySQL or
 * Spring context, while still proving the converter is wired on the field.
 */
class EncryptedFieldsCoverageTest {

    @Test
    void backupRecordFilePathEncrypted() throws Exception {
        assertEncrypted(BackupRecord.class, "filePath");
    }

    @Test
    void backupRecordNotesEncrypted() throws Exception {
        assertEncrypted(BackupRecord.class, "notes");
    }

    @Test
    void anomalyAlertDescriptionEncrypted() throws Exception {
        assertEncrypted(AnomalyAlert.class, "description");
    }

    /**
     * Independent property test on the converter itself: writing a value through the
     * converter must produce ciphertext that does NOT contain the plaintext, and the
     * reverse mapping must round-trip exactly.
     */
    @Test
    void converterRoundTripsAndDoesNotLeakPlaintext() {
        // Bring the singleton up by instantiating EncryptionService directly. The
        // production wiring uses @PostConstruct; in tests we call publishInstance manually.
        EncryptionService svc = new EncryptionService("retail-campaign-aes-key-32chars!!");
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(svc, "publishInstance");

        EncryptedStringConverter converter = new EncryptedStringConverter();
        String plain = "Spring promo backup at /var/lib/mysql/backup-2026.sql.gz";

        String ciphertext = converter.convertToDatabaseColumn(plain);
        assertThat(ciphertext).isNotEqualTo(plain);
        assertThat(ciphertext).doesNotContain(plain);
        // Base64 encoding only — no spaces, slashes are allowed but full plaintext should be gone.
        assertThat(ciphertext).doesNotContain("Spring promo");

        String decrypted = converter.convertToEntityAttribute(ciphertext);
        assertThat(decrypted).isEqualTo(plain);
    }

    private void assertEncrypted(Class<?> entityClass, String fieldName) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        Convert convert = field.getAnnotation(Convert.class);
        assertThat(convert)
                .as("%s.%s must carry @Convert(EncryptedStringConverter.class)",
                        entityClass.getSimpleName(), fieldName)
                .isNotNull();
        assertThat(convert.converter()).isEqualTo(EncryptedStringConverter.class);
    }
}

package com.meridian.retail.service;

import com.meridian.retail.storage.FileValidationService;
import com.meridian.retail.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationServiceTest {

    private final FileValidationService svc = new FileValidationService();

    @BeforeEach
    void setMax() {
        ReflectionTestUtils.setField(svc, "maxFileSizeBytes", 52428800L);
    }

    @Test
    void detectPng() {
        // 8-byte PNG magic + minimal IHDR
        byte[] png = new byte[] {
                (byte)0x89, 'P','N','G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D,'I','H','D','R',
                0x00,0x00,0x00,0x01, 0x00,0x00,0x00,0x01,
                0x08, 0x02, 0x00, 0x00, 0x00
        };
        String mime = svc.detectAndValidateMime(new ByteArrayInputStream(png));
        assertThat(mime).isEqualTo("image/png");
    }

    @Test
    void detectPdf() {
        byte[] pdf = "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n1 0 obj<<>>endobj\n%%EOF".getBytes();
        String mime = svc.detectAndValidateMime(new ByteArrayInputStream(pdf));
        assertThat(mime).isEqualTo("application/pdf");
    }

    @Test
    void rejectExecutable() {
        // ELF magic
        byte[] elf = new byte[] { 0x7f, 'E', 'L', 'F', 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        assertThatThrownBy(() -> svc.detectAndValidateMime(new ByteArrayInputStream(elf)))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void sizeChecks() {
        assertThatCode(() -> svc.validateSize(1024)).doesNotThrowAnyException();
        assertThatThrownBy(() -> svc.validateSize(0)).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> svc.validateSize(60_000_000)).isInstanceOf(StorageException.class);
    }
}

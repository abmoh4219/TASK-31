package com.meridian.retail.storage;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * File validation by content signature (Tika), not by file extension.
 *
 * SPEC.md: "checked using local file signatures and hashes rather than cloud scanning".
 * Tika sniffs the magic bytes from the stream so an attacker cannot bypass the check by
 * renaming a .exe to .pdf.
 *
 * Allowed types: PDF, JPEG, PNG, GIF (flyers, terms PDFs, store signage images).
 * Maximum size: configurable, default 50 MB.
 */
@Service
public class FileValidationService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/gif"
    );

    private final Tika tika = new Tika();

    @Value("${app.upload.max-file-size-bytes:52428800}")
    private long maxFileSizeBytes;

    /**
     * Detects MIME type from the input stream's content. Throws StorageException if the
     * content is not in the allowed list.
     */
    public String detectAndValidateMime(InputStream input) {
        try {
            String mime = tika.detect(input);
            if (mime == null || !ALLOWED_MIME.contains(mime)) {
                throw new StorageException("Disallowed file type: " + mime);
            }
            return mime;
        } catch (IOException e) {
            throw new StorageException("Failed to read upload for type detection", e);
        }
    }

    public void validateSize(long bytes) {
        if (bytes <= 0) {
            throw new StorageException("Empty upload");
        }
        if (bytes > maxFileSizeBytes) {
            throw new StorageException("File exceeds maximum size of " + maxFileSizeBytes + " bytes");
        }
    }

    public boolean isAllowedMime(String mime) {
        return mime != null && ALLOWED_MIME.contains(mime);
    }
}

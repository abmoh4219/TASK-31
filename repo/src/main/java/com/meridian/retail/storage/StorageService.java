package com.meridian.retail.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;

/**
 * Filesystem operations for chunked uploads and final storage.
 *
 * Layout:
 *   /app/uploads/{campaignId}/{storedFilename}        — finalized files
 *   /app/uploads/tmp/{uploadId}/chunk-{idx}           — pending chunks during upload
 *
 * SPEC.md: stored locally, no cloud, permission inherited from parent campaign,
 * SHA-256 computed at finalization for integrity.
 */
@Service
@Slf4j
public class StorageService {

    @Value("${app.upload.path:/app/uploads}")
    private String uploadRoot;

    /** Final storage directory for {campaignId}, created on demand. */
    public Path getCampaignDir(Long campaignId) {
        Path p = Paths.get(uploadRoot, String.valueOf(campaignId));
        ensureDir(p);
        return p;
    }

    /** Per-upload temp directory keyed by uploadId, created on demand. */
    public Path getTempDir(String uploadId) {
        Path p = Paths.get(uploadRoot, "tmp", uploadId);
        ensureDir(p);
        return p;
    }

    /** Persists a single chunk to the temp directory. */
    public Path storeChunk(String uploadId, int chunkIndex, byte[] bytes) {
        Path dir = getTempDir(uploadId);
        Path target = dir.resolve("chunk-" + chunkIndex);
        try {
            Files.write(target, bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            return target;
        } catch (IOException e) {
            throw new StorageException("Failed to write chunk " + chunkIndex + " for upload " + uploadId, e);
        }
    }

    /**
     * Concatenates chunks 0..(totalChunks-1) into the destination file in order. Aborts
     * with StorageException if any chunk is missing.
     */
    public Path assembleChunks(String uploadId, int totalChunks, Path destination) {
        Path tempDir = getTempDir(uploadId);
        try {
            if (Files.exists(destination)) Files.delete(destination);
            Files.createDirectories(destination.getParent());
            try (var out = Files.newOutputStream(destination,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunk = tempDir.resolve("chunk-" + i);
                    if (!Files.exists(chunk)) {
                        throw new StorageException("Missing chunk " + i + " for upload " + uploadId);
                    }
                    Files.copy(chunk, out);
                }
            }
            return destination;
        } catch (IOException e) {
            throw new StorageException("Failed to assemble chunks for upload " + uploadId, e);
        }
    }

    /** Hex SHA-256 of the file at {@code path}. */
    public String computeSha256(Path path) {
        try (var in = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new StorageException("Failed to compute SHA-256 for " + path, e);
        }
    }

    /** Removes the temp dir for an upload (best-effort). */
    public void cleanupTemp(String uploadId) {
        Path dir = Paths.get(uploadRoot, "tmp", uploadId);
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            log.warn("cleanupTemp failed for {}: {}", uploadId, e.getMessage());
        }
    }

    /** Moves a file (used when storing watermarked outputs etc.) */
    public void move(Path source, Path destination) {
        try {
            Files.createDirectories(destination.getParent());
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to move " + source + " -> " + destination, e);
        }
    }

    private void ensureDir(Path p) {
        try {
            if (!Files.exists(p)) Files.createDirectories(p);
        } catch (IOException e) {
            throw new StorageException("Failed to create directory " + p, e);
        }
    }
}

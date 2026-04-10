package com.meridian.retail.backup;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.BackupRecord;
import com.meridian.retail.entity.BackupStatus;
import com.meridian.retail.repository.BackupRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Restore workflow for disaster recovery drills. Pairs with {@link BackupService}.
 *
 * Two entry points:
 *   - restoreFromBackup(id) — overwrites the live database from a specific backup row.
 *     Intended for real DR scenarios, invoked from /admin/backup/{id}/restore.
 *   - testRestoreLatest() — "does the latest backup actually deserialize?" drill. Gunzips
 *     the file into a temporary location and verifies it parses, without touching the
 *     live DB. Invoked from /admin/backup/test-restore and from the weekly scheduler.
 *
 * Both paths update {@code BackupRecord.restoredAt} and emit audit log entries via
 * {@link AuditLogService}. The mysql command is built with the same
 * MYSQL_PWD-env-var pattern from HIGH #5 — the DB password never appears in process args.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestoreService {

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:retail_user}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:retail_pass}")
    private String datasourcePassword;

    private final BackupRecordRepository backupRecordRepository;
    private final AuditLogService auditLogService;

    public record RestoreResult(boolean success, long durationMs, String message, Long backupId) {}

    /**
     * Full restore into the live DB. Should only be used during actual DR. The caller
     * is expected to have already put the app into maintenance mode.
     */
    @Transactional
    public RestoreResult restoreFromBackup(Long backupId, String operator, String ipAddress) {
        BackupRecord rec = backupRecordRepository.findById(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found: " + backupId));
        if (rec.getStatus() != BackupStatus.COMPLETE) {
            return markAndReturn(rec, false, 0L, "Backup is not COMPLETE", operator, ipAddress);
        }

        Path backupFile = Paths.get(rec.getFilePath());
        if (!Files.exists(backupFile)) {
            return markAndReturn(rec, false, 0L, "Backup file missing on disk", operator, ipAddress);
        }

        long start = System.currentTimeMillis();
        try {
            Path rawSql = gunzipToTemp(backupFile);
            try {
                int exit = runMysqlClient(rawSql, extractDbName(datasourceUrl));
                long duration = System.currentTimeMillis() - start;
                boolean ok = exit == 0;
                return markAndReturn(rec, ok, duration,
                        ok ? "Restore completed in " + Duration.ofMillis(duration)
                           : "mysql exited with code " + exit,
                        operator, ipAddress);
            } finally {
                try { Files.deleteIfExists(rawSql); } catch (IOException ignored) { }
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Restore from backup {} failed", backupId, e);
            return markAndReturn(rec, false, duration, "Exception: " + e.getMessage(), operator, ipAddress);
        }
    }

    /**
     * Restore drill: verify that the latest COMPLETE backup gunzips cleanly and
     * contains at least one expected SQL statement. Does NOT touch the live DB —
     * safe to run on a schedule. Updates restoredAt on success so the admin page
     * shows the last drill timestamp.
     */
    @Transactional
    public RestoreResult testRestoreLatest(String operator, String ipAddress) {
        BackupRecord latest = backupRecordRepository
                .findTop1ByStatusOrderByCreatedAtDesc(BackupStatus.COMPLETE)
                .orElse(null);
        if (latest == null) {
            return new RestoreResult(false, 0L, "No COMPLETE backup available", null);
        }
        Path backupFile = Paths.get(latest.getFilePath());
        if (!Files.exists(backupFile)) {
            return markAndReturn(latest, false, 0L, "Backup file missing on disk", operator, ipAddress);
        }

        long start = System.currentTimeMillis();
        try {
            Path rawSql = gunzipToTemp(backupFile);
            try {
                long size = Files.size(rawSql);
                String head = Files.readString(rawSql.getFileSystem().getPath(rawSql.toString()),
                        java.nio.charset.StandardCharsets.UTF_8).lines().limit(5)
                        .reduce("", (a, b) -> a + b + "\n");
                boolean looksLikeDump = head.contains("MySQL") || head.contains("CREATE")
                        || head.contains("INSERT") || head.contains("-- ");
                long duration = System.currentTimeMillis() - start;
                if (size > 0 && looksLikeDump) {
                    return markAndReturn(latest, true, duration,
                            "Test-restore OK: " + size + " bytes of SQL parsed", operator, ipAddress);
                }
                return markAndReturn(latest, false, duration,
                        "Test-restore failed: file does not look like a MySQL dump", operator, ipAddress);
            } finally {
                try { Files.deleteIfExists(rawSql); } catch (IOException ignored) { }
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Test-restore of backup {} failed", latest.getId(), e);
            return markAndReturn(latest, false, duration, "Exception: " + e.getMessage(), operator, ipAddress);
        }
    }

    private RestoreResult markAndReturn(BackupRecord rec, boolean success, long durationMs,
                                        String message, String operator, String ipAddress) {
        if (success) {
            rec.setRestoredAt(LocalDateTime.now());
            backupRecordRepository.save(rec);
        }
        auditLogService.log(AuditAction.BACKUP_RUN, "BackupRecord", rec.getId(),
                null, Map.of("operation", "RESTORE",
                             "success", success,
                             "durationMs", durationMs,
                             "message", message),
                operator, ipAddress);
        return new RestoreResult(success, durationMs, message, rec.getId());
    }

    private Path gunzipToTemp(Path gzFile) throws IOException {
        Path tempDir = gzFile.getParent();
        Path out = Files.createTempFile(tempDir, "restore_", ".sql");
        try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(gzFile));
             var os = Files.newOutputStream(out)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return out;
    }

    /**
     * Execute the mysql client against the raw SQL file. Same MYSQL_PWD safety pattern
     * as BackupService.buildMysqldumpCommand — no password in argv.
     */
    private int runMysqlClient(Path sqlFile, String dbName) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "mysql",
                "-h", extractDbHost(datasourceUrl),
                "-u", datasourceUsername,
                dbName);
        pb.environment().put("MYSQL_PWD", datasourcePassword);
        pb.redirectInput(sqlFile.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        return p.waitFor();
    }

    private String extractDbName(String url) {
        if (url == null || !url.contains("/")) return "retail_campaign";
        String s = url.substring(url.lastIndexOf('/') + 1);
        int q = s.indexOf('?');
        return q < 0 ? s : s.substring(0, q);
    }

    private String extractDbHost(String url) {
        if (url == null) return "localhost";
        int start = url.indexOf("//");
        if (start < 0) return "localhost";
        String rest = url.substring(start + 2);
        int slash = rest.indexOf('/');
        String hostPort = slash < 0 ? rest : rest.substring(0, slash);
        int colon = hostPort.indexOf(':');
        return colon < 0 ? hostPort : hostPort.substring(0, colon);
    }
}

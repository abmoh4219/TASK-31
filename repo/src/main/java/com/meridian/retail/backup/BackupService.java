package com.meridian.retail.backup;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.BackupRecord;
import com.meridian.retail.entity.BackupStatus;
import com.meridian.retail.repository.BackupRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Nightly local MySQL backup via mysqldump + gzip.
 *
 * Scheduled at 02:00 server time. Manually triggerable from /admin/backup/run.
 *
 * Recovery procedure (also documented in README.md):
 *   1. docker compose down
 *   2. Locate the latest .sql.gz under /app/backups/
 *   3. docker compose up mysql
 *   4. gunzip < backup.sql.gz | docker exec -i mysql mysql -uretail_user -pretail_pass retail_campaign
 *   5. docker compose up app
 *
 * Retention: files older than {@code app.backup.retention-days} (default 14) are deleted
 * from disk and their BackupRecord rows are flipped to status=DELETED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    @Value("${app.backup.path:/app/backups}")
    private String backupPath;

    @Value("${app.backup.retention-days:14}")
    private int retentionDays;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:retail_user}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:retail_pass}")
    private String datasourcePassword;

    private final BackupRecordRepository backupRecordRepository;
    private final AuditLogService auditLogService;

    /** Nightly cron at 02:00 server time. */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public BackupRecord runNightlyBackup() {
        return runBackup("scheduled");
    }

    /** Manual trigger from the admin UI. */
    @Transactional
    public BackupRecord runManualBackup(String operatorUsername, String ipAddress) {
        BackupRecord r = runBackup(operatorUsername == null ? "admin" : operatorUsername);
        auditLogService.log(AuditAction.BACKUP_RUN, "BackupRecord", r.getId(),
                null, java.util.Map.of("filename", r.getFilename(), "status", r.getStatus().name()),
                operatorUsername, ipAddress);
        return r;
    }

    private BackupRecord runBackup(String operator) {
        try {
            Path dir = Paths.get(backupPath);
            if (!Files.exists(dir)) Files.createDirectories(dir);

            String filename = "backup_" + LocalDate.now() + "_" + System.currentTimeMillis() + ".sql.gz";
            Path target = dir.resolve(filename);

            String dbName = extractDbName(datasourceUrl);
            String dbHost = extractDbHost(datasourceUrl);

            // mysqldump <db> | gzip > target  — invoked via /bin/sh -c so the pipe works.
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c",
                    "mysqldump -h " + dbHost
                            + " -u " + datasourceUsername
                            + " -p" + datasourcePassword
                            + " " + dbName
                            + " 2>/dev/null | gzip > " + target.toAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();

            BackupStatus status = (exit == 0 && Files.exists(target) && Files.size(target) > 0)
                    ? BackupStatus.COMPLETE
                    : BackupStatus.FAILED;

            long size = Files.exists(target) ? Files.size(target) : 0;
            String sha256 = (status == BackupStatus.COMPLETE) ? sha256Hex(target) : null;

            BackupRecord rec = BackupRecord.builder()
                    .filename(filename)
                    .filePath(target.toString())
                    .fileSizeBytes(size)
                    .sha256Checksum(sha256)
                    .status(status)
                    .notes("Initiated by " + operator + ", exit=" + exit)
                    .build();
            BackupRecord saved = backupRecordRepository.save(rec);

            // Retention sweep
            pruneExpired();
            return saved;
        } catch (Exception e) {
            log.error("Backup failed: {}", e.getMessage(), e);
            BackupRecord rec = BackupRecord.builder()
                    .filename("backup_failed_" + System.currentTimeMillis())
                    .filePath("")
                    .fileSizeBytes(0)
                    .status(BackupStatus.FAILED)
                    .notes("Exception: " + e.getMessage())
                    .build();
            return backupRecordRepository.save(rec);
        }
    }

    private void pruneExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<BackupRecord> stale = backupRecordRepository.findByStatusAndCreatedAtBefore(BackupStatus.COMPLETE, cutoff);
        for (BackupRecord r : stale) {
            try {
                Files.deleteIfExists(Paths.get(r.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete expired backup {}: {}", r.getFilePath(), e.getMessage());
            }
            r.setStatus(BackupStatus.DELETED);
            backupRecordRepository.save(r);
        }
    }

    private String extractDbName(String url) {
        if (url == null || !url.contains("/")) return "retail_campaign";
        String s = url.substring(url.lastIndexOf('/') + 1);
        int q = s.indexOf('?');
        return q < 0 ? s : s.substring(0, q);
    }

    private String extractDbHost(String url) {
        if (url == null) return "localhost";
        // jdbc:mysql://host:port/db?...
        int start = url.indexOf("//");
        if (start < 0) return "localhost";
        String rest = url.substring(start + 2);
        int slash = rest.indexOf('/');
        String hostPort = slash < 0 ? rest : rest.substring(0, slash);
        int colon = hostPort.indexOf(':');
        return colon < 0 ? hostPort : hostPort.substring(0, colon);
    }

    private String sha256Hex(Path path) {
        try (var in = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }
}

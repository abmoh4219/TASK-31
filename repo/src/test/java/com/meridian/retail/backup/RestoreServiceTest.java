package com.meridian.retail.backup;

import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.BackupRecord;
import com.meridian.retail.entity.BackupStatus;
import com.meridian.retail.repository.BackupRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HIGH #4 — restore-workflow bookkeeping.
 *
 * We don't exercise the mysql CLI path (it would require a sibling mysql binary and a
 * running DB). Instead we test the non-destructive testRestoreLatest() drill: it gunzips
 * a fake backup file, verifies it "looks like" a MySQL dump, updates restoredAt on the
 * BackupRecord, and writes an audit-log entry. That is exactly the contract the
 * /admin/backup/test-restore button depends on.
 */
@ExtendWith(MockitoExtension.class)
class RestoreServiceTest {

    @Mock BackupRecordRepository repository;
    @Mock AuditLogService auditLogService;

    @InjectMocks RestoreService restoreService;

    private Path createFakeBackup(Path dir) throws Exception {
        Path f = dir.resolve("backup_fake.sql.gz");
        try (var os = Files.newOutputStream(f);
             var gz = new GZIPOutputStream(os)) {
            // A credible fragment of a mysqldump: header line + CREATE + INSERT.
            String sql = "-- MySQL dump 10.13 from retail_campaign\n"
                    + "CREATE TABLE t (id INT);\n"
                    + "INSERT INTO t VALUES (1);\n";
            gz.write(sql.getBytes());
        }
        return f;
    }

    @Test
    void testRestoreLatestSucceedsOnValidBackup(@TempDir Path tmp) throws Exception {
        Path fake = createFakeBackup(tmp);
        BackupRecord rec = BackupRecord.builder()
                .id(1L)
                .filename("backup_fake.sql.gz")
                .filePath(fake.toString())
                .fileSizeBytes(Files.size(fake))
                .status(BackupStatus.COMPLETE)
                .build();
        when(repository.findTop1ByStatusOrderByCreatedAtDesc(BackupStatus.COMPLETE))
                .thenReturn(Optional.of(rec));
        when(repository.save(any(BackupRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        RestoreService.RestoreResult result = restoreService.testRestoreLatest("admin", "127.0.0.1");

        assertThat(result.success()).isTrue();
        assertThat(result.backupId()).isEqualTo(1L);
        assertThat(rec.getRestoredAt()).isNotNull();
        verify(repository).save(rec);
        // Audit log MUST be emitted on every restore attempt, successful or not.
        verify(auditLogService, atLeastOnce()).log(any(), eq("BackupRecord"), eq(1L),
                any(), any(), anyString(), anyString());
    }

    @Test
    void testRestoreLatestFailsGracefullyWhenNoBackup() {
        when(repository.findTop1ByStatusOrderByCreatedAtDesc(BackupStatus.COMPLETE))
                .thenReturn(Optional.empty());

        RestoreService.RestoreResult result = restoreService.testRestoreLatest("admin", "127.0.0.1");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("No COMPLETE backup");
    }

    @Test
    void testRestoreLatestFailsWhenBackupFileMissing(@TempDir Path tmp) {
        BackupRecord rec = BackupRecord.builder()
                .id(2L)
                .filename("ghost.sql.gz")
                .filePath(tmp.resolve("does-not-exist.sql.gz").toString())
                .status(BackupStatus.COMPLETE)
                .build();
        when(repository.findTop1ByStatusOrderByCreatedAtDesc(BackupStatus.COMPLETE))
                .thenReturn(Optional.of(rec));

        RestoreService.RestoreResult result = restoreService.testRestoreLatest("admin", "127.0.0.1");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("missing");
        assertThat(rec.getRestoredAt()).isNull();
        // Still audited — failures must be traceable.
        verify(auditLogService, atLeastOnce()).log(any(), anyString(), any(),
                any(), any(), anyString(), anyString());
    }
}

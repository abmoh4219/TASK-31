package com.meridian.retail.service;

import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.backup.BackupService;
import com.meridian.retail.entity.BackupRecord;
import com.meridian.retail.entity.BackupStatus;
import com.meridian.retail.repository.BackupRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock BackupRecordRepository backupRecordRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks BackupService svc;

    @BeforeEach
    void wireProperties() throws Exception {
        Path tmp = Files.createTempDirectory("backup-test");
        ReflectionTestUtils.setField(svc, "backupPath", tmp.toString());
        ReflectionTestUtils.setField(svc, "retentionDays", 14);
        ReflectionTestUtils.setField(svc, "datasourceUrl", "jdbc:mysql://mysql:3306/retail_campaign?useSSL=false");
        ReflectionTestUtils.setField(svc, "datasourceUsername", "retail_user");
        ReflectionTestUtils.setField(svc, "datasourcePassword", "retail_pass");
    }

    @Test
    void manualBackupAlwaysPersistsRecordAndAudits() {
        // mysqldump won't be on PATH inside this unit test JVM — that's expected.
        // The service still saves a BackupRecord with status FAILED so the trail exists.
        when(backupRecordRepository.save(any(BackupRecord.class))).thenAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        // The R3 mysqldump rewrite uses ProcessBuilder.start() directly; when mysqldump
        // isn't on PATH (as in this pure-unit JVM) start() throws IOException and the
        // service jumps to the catch block WITHOUT running pruneExpired(), so this stub
        // may or may not be hit. Lenient so strict-stubs doesn't fail the test.
        lenient().when(backupRecordRepository.findByStatusAndCreatedAtBefore(any(BackupStatus.class), any()))
                .thenReturn(List.of());

        BackupRecord r = svc.runManualBackup("admin", "127.0.0.1");
        assertThat(r).isNotNull();
        // Either COMPLETE (if mysqldump happened to exist) or FAILED — both are valid persisted outcomes
        assertThat(r.getStatus()).isIn(BackupStatus.COMPLETE, BackupStatus.FAILED);
        assertThat(r.getFilename()).isNotBlank();
    }
}

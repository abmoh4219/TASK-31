package com.meridian.retail.unit.backup;

import com.meridian.retail.backup.*;
import com.meridian.retail.service.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HIGH #5 regression: the DB password must NEVER appear on the mysqldump command line
 * (visible via procfs cmdline, ps output, shell history). It must only be supplied
 * via the MYSQL_PWD environment variable inside the ProcessBuilder.
 */
class BackupCommandSafetyTest {

    @Test
    void mysqldumpCommandDoesNotContainPasswordAsArgument() {
        String password = "s3cret-pw-not-visible";
        Path outFile = Paths.get("/tmp/backup.sql");

        ProcessBuilder pb = BackupService.buildMysqldumpCommand(
                "db.internal", "retail_user", password, "retail_campaign", outFile);

        // No argument should contain the literal password, and crucially no arg should
        // start with "-p" (the unsafe inline-password flag).
        for (String arg : pb.command()) {
            assertThat(arg).doesNotContain(password);
            assertThat(arg).isNotEqualTo("-p" + password);
            assertThat(arg).doesNotMatch("^-p.+");
        }

        // The password MUST be present in the environment instead.
        assertThat(pb.environment())
                .containsEntry("MYSQL_PWD", password);
    }

    @Test
    void mysqldumpCommandUsesResultFileNotShellPipe() {
        // Confirm we no longer go through /bin/sh -c "...pipe..." which would re-introduce
        // the password in the shell argv and allow injection via db host/user.
        ProcessBuilder pb = BackupService.buildMysqldumpCommand(
                "db.internal", "retail_user", "pw", "retail_campaign", Paths.get("/tmp/x.sql"));

        assertThat(pb.command().get(0)).isEqualTo("mysqldump");
        assertThat(pb.command()).noneMatch(a -> a.equals("/bin/sh"));
        assertThat(pb.command()).noneMatch(a -> a.equals("-c"));
        assertThat(pb.command()).anyMatch(a -> a.startsWith("--result-file="));
    }
}

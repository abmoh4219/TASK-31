package com.meridian.retail.entity;

import com.meridian.retail.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "backup_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    /**
     * Absolute path to the backup file on disk. Encrypted at rest via
     * {@link EncryptedStringConverter} so that an attacker reading a DB dump cannot learn
     * where on the filesystem to fetch actual backups. Encrypt/decrypt happens transparently
     * through the JPA converter.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BackupStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "restored_at")
    private LocalDateTime restoredAt;

    /**
     * Backup notes can include the operator name, exit codes and exception messages
     * (system internals). Encrypted at rest alongside file_path so a leaked DB dump
     * does not reveal infrastructure details.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

package com.meridian.retail.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "used_nonces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsedNonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nonce", nullable = false, length = 128, unique = true)
    private String nonce;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    void onCreate() {
        if (usedAt == null) usedAt = LocalDateTime.now();
    }
}

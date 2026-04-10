package com.meridian.retail.security;

import com.meridian.retail.repository.UsedNonceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Hourly cleanup of expired anti-replay nonces. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsedNonceCleanupTask {

    private final UsedNonceRepository usedNonceRepository;

    /** Runs at the top of every hour. cron: sec min hour dayOfMonth month dayOfWeek */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanup() {
        int removed = usedNonceRepository.deleteExpired(LocalDateTime.now());
        if (removed > 0) {
            log.info("UsedNonceCleanupTask: removed {} expired nonces", removed);
        }
    }
}

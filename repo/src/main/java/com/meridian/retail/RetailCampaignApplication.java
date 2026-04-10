package com.meridian.retail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Retail Campaign Governance & Content Integrity System.
 * Offline-first, on-premise back-office tool for brick-and-mortar retailers.
 *
 * @SpringBootApplication wires component scan, auto-config and configuration.
 * @EnableScheduling enables the @Scheduled cleanup, anomaly detection and backup tasks.
 */
@SpringBootApplication
@EnableScheduling
public class RetailCampaignApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailCampaignApplication.class, args);
    }
}

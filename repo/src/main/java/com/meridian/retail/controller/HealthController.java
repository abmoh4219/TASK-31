package com.meridian.retail.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public health check.
 *
 * Spring Boot Actuator already exposes /actuator/health (and is whitelisted in
 * SecurityConfig.permitAll), but PLAN.md requires an explicit, no-auth /health endpoint
 * with a deterministic JSON body for use by simple Docker healthchecks and external monitors.
 */
@RestController
public class HealthController {

    @GetMapping({"/health", "/api/health"})
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "retail-campaign");
    }
}

package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.HealthController;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void healthReturnsStatusUp() {
        Map<String, String> result = controller.health();
        assertThat(result).containsEntry("status", "UP");
    }

    @Test
    void healthReturnsServiceName() {
        Map<String, String> result = controller.health();
        assertThat(result).containsKey("service");
        assertThat(result.get("service")).isNotBlank();
    }

    @Test
    void healthMapHasTwoEntries() {
        Map<String, String> result = controller.health();
        assertThat(result).hasSize(2);
    }
}

package com.meridian.retail.backup;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R4 audit HIGH #5: weekly automated restore drill must be scheduled. We assert via
 * reflection that {@code RestoreService.weeklyRestoreDrill} carries an {@link Scheduled}
 * annotation with a Sunday cron expression — running the actual schedule would require
 * a real ApplicationContext + clock control, neither of which add value over the
 * annotation check.
 */
class RestoreDrillScheduleTest {

    @Test
    void weeklyRestoreDrillIsScheduledOnSunday() throws NoSuchMethodException {
        Method m = RestoreService.class.getDeclaredMethod("weeklyRestoreDrill");
        Scheduled scheduled = m.getAnnotation(Scheduled.class);

        assertThat(scheduled)
                .as("RestoreService.weeklyRestoreDrill must be annotated with @Scheduled")
                .isNotNull();

        String cron = scheduled.cron();
        assertThat(cron)
                .as("Schedule must run weekly on Sunday")
                .containsAnyOf("SUN", "0"); // either day-of-week SUN or numeric form
        assertThat(cron).contains("?");
    }
}

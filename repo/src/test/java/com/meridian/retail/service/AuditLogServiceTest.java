package com.meridian.retail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.AuditLog;
import com.meridian.retail.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock AuditLogRepository repo;
    AuditLogService svc;

    @org.junit.jupiter.api.BeforeEach
    void init() {
        svc = new AuditLogService(repo, new ObjectMapper());
    }

    @Test
    void logSerializesBeforeAndAfter() {
        when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        svc.log(AuditAction.CAMPAIGN_CREATED, "Campaign", 42L,
                Map.of("name", "old"), Map.of("name", "new"),
                "alice", "127.0.0.1");

        verify(repo).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("CAMPAIGN_CREATED");
        assertThat(saved.getEntityType()).isEqualTo("Campaign");
        assertThat(saved.getEntityId()).isEqualTo(42L);
        assertThat(saved.getOperatorUsername()).isEqualTo("alice");
        assertThat(saved.getBeforeState()).contains("\"name\":\"old\"");
        assertThat(saved.getAfterState()).contains("\"name\":\"new\"");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void repositoryHasNoUpdateOrDeleteMethods() {
        // Defence in depth: AuditLogRepository must NOT expose any update or delete operation
        // outside the JpaRepository defaults. We assert that no custom delete/update methods
        // were added to the interface.
        Method[] declared = AuditLogRepository.class.getDeclaredMethods();
        for (Method m : declared) {
            String name = m.getName().toLowerCase();
            assertThat(name).doesNotContain("delete");
            assertThat(name).doesNotContain("update");
        }
    }
}

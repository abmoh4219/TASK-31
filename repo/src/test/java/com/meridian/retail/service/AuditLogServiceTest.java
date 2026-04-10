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
        // R3 hardening: check the FULL method surface (including inherited),
        // because the previous check only saw locally-declared methods. The repository
        // now extends Spring Data's Repository base instead of JpaRepository, so no
        // delete*/deleteAll*/saveAll methods should be visible at all.
        Method[] all = AuditLogRepository.class.getMethods();
        for (Method m : all) {
            String name = m.getName().toLowerCase();
            assertThat(name)
                    .as("method %s must not be a delete mutator", m.getName())
                    .doesNotContain("delete");
            // "saveAll" is a JpaRepository-style batch mutator that implicitly permits
            // update via merge semantics — also banned.
            assertThat(name).isNotEqualTo("saveall");
        }
    }

    @Test
    void auditLogEntityHasNoSetters() {
        // Defence-in-depth layer 1: the entity itself must be immutable. Lombok @Setter
        // would generate public setXxx methods; we removed @Setter in R3, so none must
        // exist on AuditLog.
        Method[] methods = AuditLog.class.getMethods();
        for (Method m : methods) {
            if (m.getDeclaringClass() == Object.class) continue;
            assertThat(m.getName())
                    .as("AuditLog must have no setters")
                    .doesNotStartWith("set");
        }
    }
}

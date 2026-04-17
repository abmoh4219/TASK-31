package com.meridian.retail.unit.controller;

import com.meridian.retail.audit.SensitiveAccessLogService;
import com.meridian.retail.backup.BackupService;
import com.meridian.retail.backup.RestoreService;
import com.meridian.retail.controller.AdminController;
import com.meridian.retail.entity.*;
import com.meridian.retail.entity.RoleChangeRequest;
import com.meridian.retail.repository.*;
import com.meridian.retail.security.PasswordComplexityException;
import com.meridian.retail.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminControllerTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock SensitiveAccessLogService sensitiveAccessLogService;
    @Mock AnomalyAlertRepository anomalyAlertRepository;
    @Mock BackupRecordRepository backupRecordRepository;
    @Mock BackupService backupService;
    @Mock RestoreService restoreService;
    @Mock UserService userService;
    @Mock UserRepository userRepository;
    @Mock RoleChangeService roleChangeService;
    @Mock Model model;
    @Mock Authentication auth;
    @Mock HttpServletRequest request;
    @Mock RedirectAttributes redirect;

    AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(auditLogRepository, sensitiveAccessLogService,
                anomalyAlertRepository, backupRecordRepository, backupService, restoreService,
                userService, userRepository, roleChangeService);
        when(auth.getName()).thenReturn("admin");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── auditLog ──────────────────────────────────────────────────────────────

    @Test
    void auditLogReturnsAuditLogView() {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of()));
        String view = controller.auditLog(0, auth, request, model);
        assertThat(view).isEqualTo("audit/log");
    }

    @Test
    void auditLogLogsAccess() {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of()));
        controller.auditLog(0, auth, request, model);
        verify(sensitiveAccessLogService).logAccess(eq("audit_log"), anyString(), isNull(),
                anyString(), anyString());
    }

    // ── sensitiveLog ──────────────────────────────────────────────────────────

    @Test
    void sensitiveLogReturnsSensitiveLogView() {
        when(sensitiveAccessLogService.recent(any())).thenReturn(new PageImpl<>(List.of()));
        String view = controller.sensitiveLog(0, auth, request, model);
        assertThat(view).isEqualTo("audit/sensitive-log");
    }

    // ── anomalyAlerts ─────────────────────────────────────────────────────────

    @Test
    void anomalyAlertsReturnsAnomalyAlertsView() {
        when(anomalyAlertRepository.findAllByOrderByDetectedAtDesc()).thenReturn(List.of());
        String view = controller.anomalyAlerts(model);
        assertThat(view).isEqualTo("admin/anomaly-alerts");
    }

    // ── acknowledge ───────────────────────────────────────────────────────────

    @Test
    void acknowledgeRedirectsToAnomalyAlerts() {
        when(anomalyAlertRepository.findById(1L)).thenReturn(Optional.empty());
        String view = controller.acknowledge(1L, auth, redirect);
        assertThat(view).isEqualTo("redirect:/admin/anomaly-alerts");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void acknowledgeWithFoundAlertSavesAcknowledgement() {
        AnomalyAlert alert = mock(AnomalyAlert.class);
        when(anomalyAlertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(anomalyAlertRepository.save(alert)).thenReturn(alert);
        controller.acknowledge(1L, auth, redirect);
        verify(alert).setAcknowledgedBy("admin");
        verify(alert).setAcknowledgedAt(any());
    }

    // ── backupHistory ─────────────────────────────────────────────────────────

    @Test
    void backupHistoryReturnsBackupView() {
        when(backupRecordRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        String view = controller.backupHistory(auth, request, model);
        assertThat(view).isEqualTo("admin/backup");
    }

    // ── runBackup ─────────────────────────────────────────────────────────────

    @Test
    void runBackupRedirectsToBackup() {
        BackupRecord r = mock(BackupRecord.class);
        BackupStatus status = BackupStatus.COMPLETE;
        when(r.getStatus()).thenReturn(status);
        when(r.getFilename()).thenReturn("backup-2024.sql.gz");
        when(backupService.runManualBackup(anyString(), anyString())).thenReturn(r);
        String view = controller.runBackup(auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/backup");
    }

    // ── testRestore ───────────────────────────────────────────────────────────

    @Test
    void testRestoreWithSuccessRedirectsToBackup() {
        RestoreService.RestoreResult result = new RestoreService.RestoreResult(
                true, 500L, "OK", 1L);
        when(restoreService.testRestoreLatest(anyString(), anyString())).thenReturn(result);
        String view = controller.testRestore(auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/backup");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void testRestoreWithFailureAddsErrorMessage() {
        RestoreService.RestoreResult result = new RestoreService.RestoreResult(
                false, 0L, "No backup", null);
        when(restoreService.testRestoreLatest(anyString(), anyString())).thenReturn(result);
        String view = controller.testRestore(auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/backup");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @Test
    void restoreWithSuccessRedirectsToBackup() {
        RestoreService.RestoreResult result = new RestoreService.RestoreResult(
                true, 1000L, "Restored OK", 1L);
        when(restoreService.restoreFromBackup(anyLong(), anyString(), anyString())).thenReturn(result);
        String view = controller.restore(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/backup");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void restoreWithFailureAddsErrorMessage() {
        RestoreService.RestoreResult result = new RestoreService.RestoreResult(
                false, 0L, "Restore failed", null);
        when(restoreService.restoreFromBackup(anyLong(), anyString(), anyString())).thenReturn(result);
        String view = controller.restore(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/backup");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── listUsers ─────────────────────────────────────────────────────────────

    @Test
    void listUsersReturnsAdminUsersView() {
        when(userService.listAll()).thenReturn(List.of());
        String view = controller.listUsers(model);
        assertThat(view).isEqualTo("admin/users");
    }

    // ── newUserForm ───────────────────────────────────────────────────────────

    @Test
    void newUserFormReturnsUsersFormView() {
        String view = controller.newUserForm(model);
        assertThat(view).isEqualTo("admin/users-form");
        verify(model).addAttribute("isNew", true);
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Test
    void createUserSuccessRedirectsToUsers() {
        User saved = mock(User.class);
        when(saved.getUsername()).thenReturn("newuser");
        when(userService.createUser(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(saved);
        String view = controller.createUser("newuser", "Pass@1234!", "New User",
                UserRole.OPERATIONS, auth, request, redirect, model);
        assertThat(view).isEqualTo("redirect:/admin/users");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void createUserWithPasswordExceptionReturnsForm() {
        when(userService.createUser(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenThrow(new PasswordComplexityException("too weak"));
        String view = controller.createUser("user", "weak", null,
                UserRole.OPERATIONS, auth, request, redirect, model);
        assertThat(view).isEqualTo("admin/users-form");
        verify(model).addAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void createUserWithIllegalArgReturnsForm() {
        when(userService.createUser(anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("username exists"));
        String view = controller.createUser("admin", "Pass@1234!", null,
                UserRole.ADMIN, auth, request, redirect, model);
        assertThat(view).isEqualTo("admin/users-form");
    }

    // ── editUserForm ──────────────────────────────────────────────────────────

    @Test
    void editUserFormReturnsUsersFormView() {
        User u = mock(User.class);
        when(userService.findById(1L)).thenReturn(u);
        String view = controller.editUserForm(1L, auth, request, model);
        assertThat(view).isEqualTo("admin/users-form");
        verify(model).addAttribute(eq("editUser"), eq(u));
        verify(model).addAttribute("isNew", false);
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    void updateUserWithNoRoleChangeRedirectsToUsers() {
        User existing = mock(User.class);
        when(existing.getRole()).thenReturn(UserRole.OPERATIONS);
        when(userService.findById(1L)).thenReturn(existing);
        when(userService.updateUser(anyLong(), any(), any(), anyString(), anyString()))
                .thenReturn(existing);
        String view = controller.updateUser(1L, "New Name", UserRole.OPERATIONS,
                auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/users");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void updateUserWithRoleChangeCreatesRequest() {
        User existing = mock(User.class);
        when(existing.getRole()).thenReturn(UserRole.OPERATIONS);
        when(userService.findById(1L)).thenReturn(existing);
        when(userService.updateUser(anyLong(), any(), any(), anyString(), anyString()))
                .thenReturn(existing);
        RoleChangeRequest rcr = mock(RoleChangeRequest.class);
        when(rcr.getId()).thenReturn(42L);
        when(roleChangeService.request(anyLong(), any(), anyString(), anyString())).thenReturn(rcr);
        String view = controller.updateUser(1L, null, UserRole.REVIEWER, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/users");
        verify(roleChangeService).request(1L, UserRole.REVIEWER, "admin", "127.0.0.1");
    }

    // ── roleChangeList ────────────────────────────────────────────────────────

    @Test
    void roleChangeListReturnsRoleChangesView() {
        when(roleChangeService.listPending()).thenReturn(List.of());
        when(roleChangeService.listAll()).thenReturn(List.of());
        String view = controller.roleChangeList(model);
        assertThat(view).isEqualTo("admin/role-changes");
    }

    // ── requestRoleChange ─────────────────────────────────────────────────────

    @Test
    void requestRoleChangeSuccessRedirectsToRoleChanges() {
        RoleChangeRequest rcr = mock(RoleChangeRequest.class);
        when(rcr.getId()).thenReturn(1L);
        when(roleChangeService.request(anyLong(), any(), anyString(), anyString())).thenReturn(rcr);
        String view = controller.requestRoleChange(1L, UserRole.REVIEWER, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/role-changes");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void requestRoleChangeWithExceptionAddsError() {
        when(roleChangeService.request(anyLong(), any(), anyString(), anyString()))
                .thenThrow(new CampaignValidationException("already pending"));
        String view = controller.requestRoleChange(1L, UserRole.ADMIN, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/role-changes");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── approveRoleChangeFirst ────────────────────────────────────────────────

    @Test
    void approveRoleChangeFirstSuccessRedirects() {
        when(roleChangeService.recordFirstApproval(anyLong(), anyString(), anyString()))
                .thenReturn(mock(RoleChangeRequest.class));
        String view = controller.approveRoleChangeFirst(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/role-changes");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void approveRoleChangeFirstWithSameApproverAddsError() {
        doThrow(new SameApproverException("same approver"))
                .when(roleChangeService).recordFirstApproval(anyLong(), anyString(), anyString());
        String view = controller.approveRoleChangeFirst(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/role-changes");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── approveRoleChangeSecond ───────────────────────────────────────────────

    @Test
    void approveRoleChangeSecondSuccessRedirects() {
        when(roleChangeService.recordSecondApproval(anyLong(), anyString(), anyString()))
                .thenReturn(mock(RoleChangeRequest.class));
        String view = controller.approveRoleChangeSecond(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/role-changes");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    // ── rejectRoleChange ─────────────────────────────────────────────────────

    @Test
    void rejectRoleChangeSuccessRedirects() {
        when(roleChangeService.reject(anyLong(), anyString(), anyString()))
                .thenReturn(mock(RoleChangeRequest.class));
        String view = controller.rejectRoleChange(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/role-changes");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    // ── deactivateUser ────────────────────────────────────────────────────────

    @Test
    void deactivateUserSuccessRedirectsToUsers() {
        doNothing().when(userService).deactivateUser(anyLong(), anyString(), anyString());
        String view = controller.deactivateUser(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/admin/users");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    // ── checkUsername ─────────────────────────────────────────────────────────

    @Test
    void checkUsernameWithBlankReturnsHint() {
        String result = controller.checkUsername("  ");
        assertThat(result).contains("Pick a username");
    }

    @Test
    void checkUsernameWithAvailableUsernameReturnsAvailable() {
        when(userService.isUsernameAvailable("newuser")).thenReturn(true);
        String result = controller.checkUsername("newuser");
        assertThat(result).contains("Available");
    }

    @Test
    void checkUsernameWithTakenUsernameReturnsTaken() {
        when(userService.isUsernameAvailable("admin")).thenReturn(false);
        String result = controller.checkUsername("admin");
        assertThat(result).contains("Already taken");
    }
}

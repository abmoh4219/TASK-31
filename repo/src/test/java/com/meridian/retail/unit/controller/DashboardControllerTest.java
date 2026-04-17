package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.DashboardController;
import com.meridian.retail.entity.BackupStatus;
import com.meridian.retail.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.Model;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardControllerTest {

    @Mock CampaignRepository campaignRepository;
    @Mock ApprovalQueueRepository approvalQueueRepository;
    @Mock UserRepository userRepository;
    @Mock AnomalyAlertRepository anomalyAlertRepository;
    @Mock BackupRecordRepository backupRecordRepository;
    @Mock ContentItemRepository contentItemRepository;
    @Mock Model model;
    @Mock Authentication auth;

    DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(campaignRepository, approvalQueueRepository,
                userRepository, anomalyAlertRepository, backupRecordRepository, contentItemRepository);
    }

    private Authentication authWithRole(String role) {
        Authentication a = mock(Authentication.class);
        GrantedAuthority ga = () -> role;
        doReturn(List.of(ga)).when(a).getAuthorities();
        return a;
    }

    @Test
    void dashboardRedirectsAdminToAdminDashboard() {
        String view = controller.dashboard(authWithRole("ROLE_ADMIN"));
        assertThat(view).isEqualTo("redirect:/admin/dashboard");
    }

    @Test
    void dashboardRedirectsReviewerToApprovalQueue() {
        String view = controller.dashboard(authWithRole("ROLE_REVIEWER"));
        assertThat(view).isEqualTo("redirect:/approval/queue");
    }

    @Test
    void dashboardRedirectsFinanceToAnalytics() {
        String view = controller.dashboard(authWithRole("ROLE_FINANCE"));
        assertThat(view).isEqualTo("redirect:/analytics/dashboard");
    }

    @Test
    void dashboardRedirectsOperationsToCampaigns() {
        String view = controller.dashboard(authWithRole("ROLE_OPERATIONS"));
        assertThat(view).isEqualTo("redirect:/campaigns");
    }

    @Test
    void dashboardRedirectsCustomerServiceToCampaigns() {
        String view = controller.dashboard(authWithRole("ROLE_CUSTOMER_SERVICE"));
        assertThat(view).isEqualTo("redirect:/campaigns");
    }

    @Test
    void dashboardWithUnknownRoleRedirectsToLogin() {
        String view = controller.dashboard(authWithRole("ROLE_UNKNOWN"));
        assertThat(view).isEqualTo("redirect:/login");
    }

    @Test
    void adminDashboardReturnsAdminView() {
        when(userRepository.count()).thenReturn(5L);
        when(userRepository.countByActiveTrue()).thenReturn(4L);
        when(campaignRepository.countByStatusAndDeletedAtIsNull(any())).thenReturn(3L);
        when(approvalQueueRepository.countByStatus(any())).thenReturn(1L);
        when(anomalyAlertRepository.countByAcknowledgedAtIsNull()).thenReturn(0L);
        when(contentItemRepository.count()).thenReturn(10L);
        when(backupRecordRepository.findTop1ByStatusOrderByCreatedAtDesc(BackupStatus.COMPLETE))
                .thenReturn(Optional.empty());

        String view = controller.adminDashboard(model);
        assertThat(view).isEqualTo("dashboard/admin");
        verify(model).addAttribute(eq("totalUsers"), eq(5L));
        verify(model).addAttribute(eq("activeUsers"), eq(4L));
    }
}

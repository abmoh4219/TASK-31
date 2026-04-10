package com.meridian.retail.security;

import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.repository.ApprovalQueueRepository;
import com.meridian.retail.repository.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Object-level authorization for campaign-scoped resources (HIGH #4).
 * Attachment downloads must check these rules before issuing/serving files.
 */
@ExtendWith(MockitoExtension.class)
class CampaignAccessPolicyTest {

    @Mock CampaignRepository campaignRepository;
    @Mock ApprovalQueueRepository approvalQueueRepository;
    @InjectMocks CampaignAccessPolicy policy;

    private Authentication authAs(String user, String role) {
        return new UsernamePasswordAuthenticationToken(
                user, "n/a", List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void adminSeesEverything() {
        assertThat(policy.canAccessCampaign(1L, authAs("boss", "ROLE_ADMIN"))).isTrue();
    }

    @Test
    void financeSeesEverything() {
        assertThat(policy.canAccessCampaign(1L, authAs("cfo", "ROLE_FINANCE"))).isTrue();
    }

    @Test
    void creatorSeesOwnCampaign() {
        Campaign c = Campaign.builder().id(7L).createdBy("alice").build();
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(c));
        when(approvalQueueRepository.findByCampaignIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

        assertThat(policy.canAccessCampaign(7L, authAs("alice", "ROLE_OPERATIONS"))).isTrue();
    }

    @Test
    void assignedReviewerSeesCampaign() {
        Campaign c = Campaign.builder().id(8L).createdBy("alice").build();
        ApprovalQueue q = ApprovalQueue.builder().id(1L).campaignId(8L).assignedReviewer("bob").build();
        when(campaignRepository.findById(8L)).thenReturn(Optional.of(c));
        when(approvalQueueRepository.findByCampaignIdOrderByCreatedAtDesc(8L)).thenReturn(List.of(q));

        assertThat(policy.canAccessCampaign(8L, authAs("bob", "ROLE_REVIEWER"))).isTrue();
    }

    @Test
    void strangerDenied() {
        Campaign c = Campaign.builder().id(9L).createdBy("alice").build();
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(c));
        when(approvalQueueRepository.findByCampaignIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());

        assertThat(policy.canAccessCampaign(9L, authAs("eve", "ROLE_OPERATIONS"))).isFalse();
    }

    @Test
    void requireCampaignAccessThrowsForStranger() {
        Campaign c = Campaign.builder().id(10L).createdBy("alice").build();
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(c));
        when(approvalQueueRepository.findByCampaignIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> policy.requireCampaignAccess(10L, authAs("eve", "ROLE_OPERATIONS")))
                .isInstanceOf(AccessDeniedException.class);
    }
}

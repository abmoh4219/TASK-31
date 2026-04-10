package com.meridian.retail.security;

import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.repository.ApprovalQueueRepository;
import com.meridian.retail.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Central object-level authorization helper for campaign-scoped resources (attachments,
 * content, coupons). Attachments inherit permission from their parent campaign.
 *
 * Rules, in order:
 *   1. ADMIN — unrestricted.
 *   2. FINANCE — unrestricted READ (needed for analytics / export flows).
 *   3. Campaign creator — always has access to their own campaigns.
 *   4. Assigned reviewer of any approval queue entry for the campaign — has access.
 *   5. Otherwise — denied.
 *
 * The rule set is intentionally simple and easy to audit by reading this one class.
 */
@Component
@RequiredArgsConstructor
public class CampaignAccessPolicy {

    private final CampaignRepository campaignRepository;
    private final ApprovalQueueRepository approvalQueueRepository;

    public boolean canAccessCampaign(Long campaignId, Authentication auth) {
        if (auth == null || campaignId == null) return false;

        if (hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_FINANCE")) {
            return true;
        }

        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return false;

        String username = auth.getName();
        if (username != null && username.equalsIgnoreCase(campaign.getCreatedBy())) {
            return true;
        }

        // Reviewer assigned to any approval queue entry for this campaign counts as having access.
        List<ApprovalQueue> entries = approvalQueueRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
        for (ApprovalQueue q : entries) {
            if (q.getAssignedReviewer() != null
                    && q.getAssignedReviewer().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    /** Throws AccessDeniedException if the authenticated user cannot access the campaign. */
    public void requireCampaignAccess(Long campaignId, Authentication auth) {
        if (!canAccessCampaign(campaignId, auth)) {
            throw new AccessDeniedException("You do not have access to campaign " + campaignId);
        }
    }

    private boolean hasRole(Authentication auth, String role) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (role.equals(ga.getAuthority())) return true;
        }
        return false;
    }
}

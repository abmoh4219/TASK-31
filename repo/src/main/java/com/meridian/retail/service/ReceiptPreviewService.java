package com.meridian.retail.service;

import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.Coupon;
import com.meridian.retail.entity.DiscountType;
import com.meridian.retail.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Renders a plain-text receipt preview block for a campaign. The output is intentionally
 * fixed-width / monospace and structured the way a thermal-printer receipt would look so
 * operators can validate wording before publishing.
 *
 * The HTMX endpoint in CampaignController returns this string wrapped in a Thymeleaf
 * fragment that styles it with the receipt-preview CSS class.
 */
@Service
@RequiredArgsConstructor
public class ReceiptPreviewService {

    private final CouponRepository couponRepository;

    public String generatePreview(Campaign campaign) {
        if (campaign == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("============================================\n");
        sb.append("            MERIDIAN RETAIL STORE           \n");
        sb.append("============================================\n");
        sb.append("Campaign : ").append(safe(campaign.getName())).append("\n");
        sb.append("Type     : ").append(campaign.getType() == null ? "" : campaign.getType().name()).append("\n");
        if (campaign.getStoreId() != null) {
            sb.append("Store    : ").append(campaign.getStoreId()).append("\n");
        }
        sb.append("--------------------------------------------\n");

        if (campaign.getId() != null) {
            List<Coupon> coupons = couponRepository.findByCampaignId(campaign.getId());
            for (Coupon c : coupons) {
                sb.append("CODE: ").append(c.getCode()).append("\n");
                if (c.getDiscountType() == DiscountType.PERCENT) {
                    sb.append("  ").append(c.getDiscountValue().stripTrailingZeros().toPlainString())
                      .append("% OFF YOUR PURCHASE\n");
                } else {
                    sb.append("  $").append(c.getDiscountValue().stripTrailingZeros().toPlainString())
                      .append(" OFF\n");
                }
                if (c.getMinPurchaseAmount() != null && c.getMinPurchaseAmount().signum() > 0) {
                    sb.append("  Min purchase: $")
                      .append(c.getMinPurchaseAmount().stripTrailingZeros().toPlainString())
                      .append("\n");
                }
                sb.append("--------------------------------------------\n");
            }
        }

        if (campaign.getReceiptWording() != null && !campaign.getReceiptWording().isBlank()) {
            sb.append(campaign.getReceiptWording()).append("\n");
            sb.append("--------------------------------------------\n");
        }
        if (campaign.getStartDate() != null && campaign.getEndDate() != null) {
            sb.append("Valid: ").append(campaign.getStartDate())
              .append(" through ").append(campaign.getEndDate()).append("\n");
        }
        sb.append("============================================\n");
        sb.append("        THANK YOU FOR SHOPPING WITH US      \n");
        sb.append("============================================\n");
        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s; }
}

package com.meridian.retail.service;

/** Thrown by CampaignService and CouponService when business validation fails. */
public class CampaignValidationException extends RuntimeException {
    public CampaignValidationException(String message) {
        super(message);
    }
}

package com.meridian.retail.service;

/**
 * Thrown when a dual-approval flow detects that approver1 and approver2 are the same user,
 * or when a reviewer tries to approve a campaign they themselves submitted.
 */
public class SameApproverException extends RuntimeException {
    public SameApproverException(String message) {
        super(message);
    }
}

package com.meridian.retail.security;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Static XSS sanitizer.
 *
 * Strips ALL HTML and script content from arbitrary user input using Jsoup's
 * "none" safelist. This is intentionally aggressive — back-office forms accept
 * plain text only, no rich-text editing. For receipt wording / descriptions we
 * still want to allow line breaks, which Jsoup preserves as plain newlines after
 * cleaning.
 *
 * Called at the START of every service method that accepts user-supplied text
 * (CampaignService, CouponService, ContentService, UserService etc.). Centralising
 * sanitisation here means QA can grep for `XssInputSanitizer.sanitize` to verify
 * coverage with one query.
 */
public final class XssInputSanitizer {

    private XssInputSanitizer() {}

    /** Returns a sanitized copy of the input or null if input is null. */
    public static String sanitize(String input) {
        if (input == null) return null;
        // Safelist.none() means: no tags, no attributes, no protocols.
        // Jsoup.clean returns the body text content with HTML stripped.
        // preserveRelativeLinks=false (default) is fine because we're dropping all tags anyway.
        return Jsoup.clean(input, Safelist.none()).trim();
    }

    /** Sanitize each entry in an array (used for multi-value form fields). */
    public static String[] sanitize(String[] inputs) {
        if (inputs == null) return null;
        String[] out = new String[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            out[i] = sanitize(inputs[i]);
        }
        return out;
    }
}

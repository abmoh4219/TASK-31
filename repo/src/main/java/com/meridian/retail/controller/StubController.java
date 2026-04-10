package com.meridian.retail.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Final remaining stub: /upload sidebar shortcut redirects to /files/upload.
 * Everything else has moved to its real controller in Phases 3-6.
 */
@Controller
@RequiredArgsConstructor
public class StubController {

    @GetMapping("/upload")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String uploadLanding() {
        return "redirect:/files/upload";
    }
}

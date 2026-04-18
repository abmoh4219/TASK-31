package com.meridian.retail.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Renders the login page. Spring Security handles the actual POST /login submission.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String locked,
                        @RequestParam(required = false) String ipblocked,
                        @RequestParam(required = false) String captcha,
                        @RequestParam(required = false) String logout,
                        HttpSession session,
                        Model model) {

        if (captcha != null) {
            model.addAttribute("errorMessage",
                    "CAPTCHA was missing or incorrect. Please solve the challenge and try again.");
        } else if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password.");
        } else if (locked != null) {
            model.addAttribute("errorMessage",
                    "Account temporarily locked after too many failed attempts. Try again in 15 minutes.");
        } else if (ipblocked != null) {
            model.addAttribute("errorMessage",
                    "Too many failed attempts from your network. Access blocked for 1 hour.");
        } else if (logout != null) {
            model.addAttribute("successMessage", "You have been signed out.");
        }

        Boolean captchaRequired = (Boolean) session.getAttribute("captchaRequired");
        model.addAttribute("captchaRequired", Boolean.TRUE.equals(captchaRequired));

        return "auth/login";
    }

    @GetMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }

    /** Root route — Spring Security forwards to /login if anonymous; if authenticated, send to / via dashboard router. */
    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }
}

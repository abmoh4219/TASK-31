package com.meridian.retail.controller;

import com.meridian.retail.audit.SensitiveAccessLogService;
import com.meridian.retail.backup.BackupService;
import com.meridian.retail.entity.AnomalyAlert;
import com.meridian.retail.entity.BackupRecord;
import com.meridian.retail.entity.User;
import com.meridian.retail.entity.UserRole;
import com.meridian.retail.repository.AnomalyAlertRepository;
import com.meridian.retail.repository.AuditLogRepository;
import com.meridian.retail.repository.BackupRecordRepository;
import com.meridian.retail.repository.UserRepository;
import com.meridian.retail.security.PasswordComplexityException;
import com.meridian.retail.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * ADMIN-only console: audit log viewer, sensitive access log, anomaly alerts, backups.
 *
 * Class-level @PreAuthorize ensures the Spring Security filter chain rejects any request
 * to /admin/** that does not carry ROLE_ADMIN. URL-based authorization in SecurityConfig
 * enforces the same rule at the URL level — defence in depth.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditLogRepository auditLogRepository;
    private final SensitiveAccessLogService sensitiveAccessLogService;
    private final AnomalyAlertRepository anomalyAlertRepository;
    private final BackupRecordRepository backupRecordRepository;
    private final BackupService backupService;
    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/audit-log")
    public String auditLog(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<com.meridian.retail.entity.AuditLog> entries =
                auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, 50));
        model.addAttribute("breadcrumb", "Audit Log (ADMIN ONLY)");
        model.addAttribute("entries", entries);
        return "audit/log";
    }

    @GetMapping("/sensitive-log")
    public String sensitiveLog(@RequestParam(defaultValue = "0") int page, Model model) {
        var entries = sensitiveAccessLogService.recent(PageRequest.of(page, 50));
        model.addAttribute("breadcrumb", "Sensitive Access Log (ADMIN ONLY)");
        model.addAttribute("entries", entries);
        return "audit/sensitive-log";
    }

    @GetMapping("/anomaly-alerts")
    public String anomalyAlerts(Model model) {
        model.addAttribute("breadcrumb", "Anomaly Alerts");
        model.addAttribute("alerts", anomalyAlertRepository.findAllByOrderByDetectedAtDesc());
        return "admin/anomaly-alerts";
    }

    @PostMapping("/anomaly-alerts/{id}/ack")
    public String acknowledge(@PathVariable Long id,
                              Authentication auth,
                              RedirectAttributes redirect) {
        anomalyAlertRepository.findById(id).ifPresent(a -> {
            a.setAcknowledgedBy(auth.getName());
            a.setAcknowledgedAt(LocalDateTime.now());
            anomalyAlertRepository.save(a);
        });
        redirect.addFlashAttribute("successMessage", "Alert acknowledged.");
        return "redirect:/admin/anomaly-alerts";
    }

    @GetMapping("/backup")
    public String backupHistory(Model model) {
        model.addAttribute("breadcrumb", "Backups");
        model.addAttribute("backups", backupRecordRepository.findAllByOrderByCreatedAtDesc());
        return "admin/backup";
    }

    @PostMapping("/backup/run")
    public String runBackup(Authentication auth, HttpServletRequest httpRequest, RedirectAttributes redirect) {
        BackupRecord r = backupService.runManualBackup(auth.getName(), clientIp(httpRequest));
        redirect.addFlashAttribute("successMessage",
                "Backup " + (r.getStatus().name().equals("COMPLETE") ? "completed" : "FAILED") + ": " + r.getFilename());
        return "redirect:/admin/backup";
    }

    // ---------- User management ----------

    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("breadcrumb", "Users");
        model.addAttribute("users", userService.listAll());
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        model.addAttribute("breadcrumb", "New User");
        model.addAttribute("isNew", true);
        model.addAttribute("roles", UserRole.values());
        return "admin/users-form";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam(required = false) String fullName,
                             @RequestParam UserRole role,
                             Authentication auth,
                             HttpServletRequest httpRequest,
                             RedirectAttributes redirect,
                             Model model) {
        try {
            User saved = userService.createUser(username, password, fullName, role,
                    auth.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "User " + saved.getUsername() + " created.");
            return "redirect:/admin/users";
        } catch (PasswordComplexityException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isNew", true);
            model.addAttribute("roles", UserRole.values());
            return "admin/users-form";
        }
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        User u = userService.findById(id);
        model.addAttribute("breadcrumb", "Edit User");
        model.addAttribute("editUser", u);
        model.addAttribute("isNew", false);
        model.addAttribute("roles", UserRole.values());
        return "admin/users-form";
    }

    @PostMapping("/users/{id}/update")
    public String updateUser(@PathVariable Long id,
                             @RequestParam(required = false) String fullName,
                             @RequestParam UserRole role,
                             Authentication auth,
                             HttpServletRequest httpRequest,
                             RedirectAttributes redirect) {
        userService.updateUser(id, fullName, role, auth.getName(), clientIp(httpRequest));
        redirect.addFlashAttribute("successMessage", "User updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/deactivate")
    public String deactivateUser(@PathVariable Long id,
                                 Authentication auth,
                                 HttpServletRequest httpRequest,
                                 RedirectAttributes redirect) {
        userService.deactivateUser(id, auth.getName(), clientIp(httpRequest));
        redirect.addFlashAttribute("successMessage", "User deactivated.");
        return "redirect:/admin/users";
    }

    /** HTMX uniqueness check — returns a tiny HTML fragment for live form feedback. */
    @GetMapping(value = "/users/check-username", produces = "text/html")
    @org.springframework.web.bind.annotation.ResponseBody
    public String checkUsername(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            return "<span class='form-text'>Pick a username.</span>";
        }
        if (userService.isUsernameAvailable(username)) {
            return "<span class='field-validation-success'><i class='bi bi-check-circle'></i> Available</span>";
        }
        return "<span class='field-validation-error'><i class='bi bi-x-circle'></i> Already taken</span>";
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) return header.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}

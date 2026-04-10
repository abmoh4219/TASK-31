package com.meridian.retail.controller;

import com.meridian.retail.backup.BackupService;
import com.meridian.retail.entity.AnomalyAlert;
import com.meridian.retail.entity.BackupRecord;
import com.meridian.retail.repository.AnomalyAlertRepository;
import com.meridian.retail.repository.AuditLogRepository;
import com.meridian.retail.repository.BackupRecordRepository;
import com.meridian.retail.audit.SensitiveAccessLogService;
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

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) return header.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}

package com.meridian.retail.controller;

import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.integrity.ContentImportService;
import com.meridian.retail.integrity.ContentVersionService;
import com.meridian.retail.integrity.DuplicateDetectionService;
import com.meridian.retail.integrity.MergeService;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.ContentItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/content")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OPERATIONS','REVIEWER','ADMIN')")
public class ContentController {

    private final ContentItemRepository contentItemRepository;
    private final CampaignRepository campaignRepository;
    private final ContentImportService importService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final MergeService mergeService;
    private final ContentVersionService versionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("breadcrumb", "Content Integrity");
        List<ContentItem> items = contentItemRepository.findAll();
        model.addAttribute("items", items);
        model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
        return "content/list";
    }

    @GetMapping("/duplicates")
    public String duplicates(Model model) {
        model.addAttribute("breadcrumb", "Duplicate Groups");
        model.addAttribute("groups", duplicateDetectionService.groupDuplicates());
        return "content/duplicates";
    }

    @PostMapping("/merge")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public String merge(@RequestParam Long masterId,
                        @RequestParam("duplicateIds") List<Long> duplicateIds,
                        Authentication auth,
                        HttpServletRequest httpRequest,
                        RedirectAttributes redirect) {
        try {
            mergeService.merge(masterId, duplicateIds, auth.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage",
                    "Merged " + duplicateIds.size() + " duplicate(s) into master #" + masterId);
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/content/duplicates";
    }

    @GetMapping("/{id}/history")
    public String history(@PathVariable Long id, Model model) {
        ContentItem item = contentItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Content not found: " + id));
        model.addAttribute("breadcrumb", "Version History");
        model.addAttribute("item", item);
        model.addAttribute("versions", versionService.getHistory(id));
        return "content/history";
    }

    @PostMapping("/{id}/rollback/{version}")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public String rollback(@PathVariable Long id,
                           @PathVariable("version") int versionNum,
                           Authentication auth,
                           HttpServletRequest httpRequest,
                           RedirectAttributes redirect) {
        try {
            versionService.rollback(id, versionNum, auth.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Rolled back to version " + versionNum);
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/content/" + id + "/history";
    }

    @PostMapping("/import/csv")
    public String importCsv(@RequestParam("file") MultipartFile file,
                            @RequestParam Long campaignId,
                            Authentication auth,
                            HttpServletRequest httpRequest,
                            RedirectAttributes redirect) {
        try {
            var result = importService.importFromCsv(file, campaignId, auth.getName(), clientIp(httpRequest));
            String msg = "Imported " + result.imported() + " items"
                    + (result.duplicatesFound() > 0 ? " (" + result.duplicatesFound() + " possible duplicates)" : "");
            redirect.addFlashAttribute("successMessage", msg);
            if (!result.errors().isEmpty()) {
                redirect.addFlashAttribute("errorMessage", String.join("; ", result.errors()));
            }
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/content";
    }

    @PostMapping("/import/single")
    public String importSingle(@RequestParam Long campaignId,
                               @RequestParam String title,
                               @RequestParam String sourceUrl,
                               @RequestParam String body,
                               Authentication auth,
                               HttpServletRequest httpRequest,
                               RedirectAttributes redirect) {
        try {
            importService.importSingle(campaignId, title, sourceUrl, body, auth.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Content added.");
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/content";
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) return header.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}

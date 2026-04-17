package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.ContentController;
import com.meridian.retail.entity.ContentItem;
import com.meridian.retail.integrity.*;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.ContentItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentControllerTest {

    @Mock ContentItemRepository contentItemRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock ContentImportService importService;
    @Mock DuplicateDetectionService duplicateDetectionService;
    @Mock MergeService mergeService;
    @Mock ContentVersionService versionService;
    @Mock Model model;
    @Mock Authentication auth;
    @Mock HttpServletRequest request;
    @Mock RedirectAttributes redirect;
    @Mock MultipartFile file;

    ContentController controller;

    @BeforeEach
    void setUp() {
        controller = new ContentController(contentItemRepository, campaignRepository,
                importService, duplicateDetectionService, mergeService, versionService);
        when(auth.getName()).thenReturn("ops");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void listReturnsContentListView() {
        when(contentItemRepository.findAll()).thenReturn(List.of());
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        String view = controller.list(model);
        assertThat(view).isEqualTo("content/list");
    }

    // ── duplicates ────────────────────────────────────────────────────────────

    @Test
    void duplicatesReturnsContentDuplicatesView() {
        when(duplicateDetectionService.groupDuplicates()).thenReturn(List.of());
        String view = controller.duplicates(model);
        assertThat(view).isEqualTo("content/duplicates");
    }

    // ── merge ─────────────────────────────────────────────────────────────────

    @Test
    void mergeSuccessRedirectsToDuplicates() {
        when(mergeService.merge(anyLong(), anyList(), anyString(), anyString()))
                .thenReturn(mock(com.meridian.retail.entity.ContentMergeLog.class));
        String view = controller.merge(1L, List.of(2L, 3L), auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content/duplicates");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void mergeWithExceptionAddsError() {
        doThrow(new RuntimeException("merge failed"))
                .when(mergeService).merge(anyLong(), anyList(), anyString(), anyString());
        String view = controller.merge(1L, List.of(2L), auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content/duplicates");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── history ───────────────────────────────────────────────────────────────

    @Test
    void historyReturnsContentHistoryView() {
        ContentItem item = mock(ContentItem.class);
        when(contentItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(versionService.getHistory(1L)).thenReturn(List.of());
        String view = controller.history(1L, model);
        assertThat(view).isEqualTo("content/history");
        verify(model).addAttribute("item", item);
    }

    @Test
    void historyWithNotFoundThrowsException() {
        when(contentItemRepository.findById(999L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.history(999L, model))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── rollback ──────────────────────────────────────────────────────────────

    @Test
    void rollbackSuccessRedirectsToHistory() {
        when(versionService.rollback(anyLong(), anyInt(), anyString(), anyString()))
                .thenReturn(mock(com.meridian.retail.entity.ContentItem.class));
        String view = controller.rollback(1L, 2, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content/1/history");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void rollbackWithExceptionAddsError() {
        when(versionService.rollback(anyLong(), anyInt(), anyString(), anyString()))
                .thenThrow(new RuntimeException("version not found"));
        String view = controller.rollback(1L, 99, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content/1/history");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── importCsv ─────────────────────────────────────────────────────────────

    @Test
    void importCsvSuccessRedirectsToContent() throws Exception {
        ContentImportService.ImportResult result =
                new ContentImportService.ImportResult(3, 0, List.of());
        when(importService.importFromCsv(any(), anyLong(), anyString(), anyString()))
                .thenReturn(result);
        String view = controller.importCsv(file, 1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void importCsvWithDuplicatesAddsWarning() throws Exception {
        ContentImportService.ImportResult result =
                new ContentImportService.ImportResult(2, 1, List.of());
        when(importService.importFromCsv(any(), anyLong(), anyString(), anyString()))
                .thenReturn(result);
        String view = controller.importCsv(file, 1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content");
        verify(redirect).addFlashAttribute(eq("successMessage"), contains("duplicate"));
    }

    @Test
    void importCsvWithExceptionAddsError() throws Exception {
        when(importService.importFromCsv(any(), anyLong(), anyString(), anyString()))
                .thenThrow(new RuntimeException("invalid CSV"));
        String view = controller.importCsv(file, 1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── importSingle ──────────────────────────────────────────────────────────

    @Test
    void importSingleSuccessRedirectsToContent() {
        ContentItem item = mock(ContentItem.class);
        when(importService.importSingle(anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(item);
        String view = controller.importSingle(1L, "Title", "http://url.com", "body",
                auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void importSingleWithExceptionAddsError() {
        when(importService.importSingle(anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenThrow(new RuntimeException("import error"));
        String view = controller.importSingle(1L, "T", "http://x", "body", auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/content");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }
}
